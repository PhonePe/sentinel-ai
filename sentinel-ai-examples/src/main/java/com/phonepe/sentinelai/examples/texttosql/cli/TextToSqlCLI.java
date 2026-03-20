/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.examples.texttosql.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;

import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import com.phonepe.sentinelai.examples.texttosql.agent.SqlQueryResult;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.server.SqliteRestServer;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import com.phonepe.sentinelai.examples.texttosql.tools.LocalSqlTools;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.jetty.http.HttpHeader;
import org.jspecify.annotations.NonNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Entry point for the Text-to-SQL interactive CLI.
 *
 * <p>Run with:
 * <pre>
 * java -jar sentinel-ai-examples-cli.jar [options]
 * </pre>
 *
 * <p>On startup the CLI:
 * <ol>
 * <li>Reads the YAML credentials file ({@code .env/credentials.yaml} by default).</li>
 * <li>Initialises the SQLite database (schema + sample data) if it does not exist.</li>
 * <li>Starts the embedded Dropwizard SQLite REST server on a free port.</li>
 * <li>Extracts the bundled SQL-execution skill to a temp directory.</li>
 * <li>Builds the {@link TextToSqlAgent} with all toolboxes and the skills extension.</li>
 * <li>Enters an interactive prompt loop, streaming agent responses token-by-token.</li>
 * </ol>
 */
@Slf4j
@Command(
        name = "text-to-sql", mixinStandardHelpOptions = true, version = "1.0", description = "Interactive Text-to-SQL agent powered by Sentinel AI"
)
public class TextToSqlCLI implements Callable<Integer> {

    @Option(
            names = {
                    "--config", "-c"
            }, description = "Path to credentials YAML file (default: .env/credentials.yaml)", defaultValue = ".env/credentials.yaml"
    )
    private String configPath;

    @Option(
            names = {
                    "--skills-dir", "-s"
            }, description = "Path to skills directory. If not set, the bundled skill is extracted to a temp dir."
    )
    private String skillsDir;

    @Option(
            names = {
                    "--session-id"
            }, description = "Session ID for conversation history (default: random UUID)", defaultValue = ""
    )
    private String sessionId;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        System.exit(new CommandLine(new TextToSqlCLI()).execute(args));
    }

    @SneakyThrows
    private static CliConfig loadConfig(String configPath) {
        final Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            System.err.println("Config file not found: " + path.toAbsolutePath());
            System.err.println("Copy src/main/resources/.env/credentials.yaml.example to "
                    + path.toAbsolutePath() + " and fill in your API key.");
            System.exit(1);
        }
        final var yamlMapper = new YAMLMapper();
        return yamlMapper.readValue(path.toFile(), CliConfig.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void printBanner() {
        System.out.println("""

                ╔════════════════════════════════════════════════════╗
                ║   Sentinel AI — Text-to-SQL Agent (e-commerce DB)  ║
                ╠════════════════════════════════════════════════════╣
                ║  Ask questions in plain English about:             ║
                ║    • users, sellers, catalog, inventory, orders    ║
                ║  Type 'exit' or 'quit' to stop, Ctrl+D to EOF.    ║
                ╚════════════════════════════════════════════════════╝
                """);
    }

    private static void printStructuredResult(SqlQueryResult result, long wallClockMs) {
        System.out.println();
        System.out.println("SQL: " + result.generatedSql());
        System.out.println();
        System.out.println(result.explanation());
        System.out.println();
        if (!result.results().isEmpty()) {
            System.out.println("Results (" + result.results().size() + " rows):");
            result.results().forEach(row -> System.out.println("  " + row));
        }
        System.out.printf("%nCompleted in %d ms (agent) / %d ms (wall clock)%n",
                          result.executionTimeMs(),
                          wallClockMs);
    }

    private static void validateConfig(CliConfig config) {
        if (config.getOpenai().getApiKey() == null || config.getOpenai().getApiKey().isBlank()) {
            System.err.println("Error: openai.apiKey must be set in the credentials file.");
            System.exit(1);
        }
    }

    @Override
    @SneakyThrows
    public Integer call() {
        // 1. Load config
        final CliConfig config = loadConfig(configPath);
        validateConfig(config);

        // 2. Resolve session ID
        final String effectiveSessionId = sessionId == null || sessionId.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : sessionId;

        // 3. Shared Jackson mapper
        final ObjectMapper mapper = JsonUtils.createMapper();

        // 4. Initialise database
        final Path dbPath = Paths.get(config.getDatabase().getPath()).toAbsolutePath();
        DatabaseInitializer.ensureInitialised(dbPath);

        // 5. Start embedded REST server
        final int port = SqliteRestServer.findFreePort();
        final String baseUrl = SqliteRestServer.startEmbedded(dbPath.toString(), port, mapper);
        log.info("SQLite REST server ready at {}", baseUrl);

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        final var httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .addInterceptor(new Interceptor() {
                    @Override
                    public @NonNull Response intercept(Interceptor.Chain chain) throws IOException {
                        log.debug("Incoming request: {} {}", chain.request().method(), chain.request().url());
                        log.debug("Headers: {}", chain.request().headers());
                        log.debug("Auth header: {}", chain.request().header(HttpHeader.AUTHORIZATION.name()));
                        val newRequest = chain.request()
                                .newBuilder()
                                .removeHeader(HttpHeader.AUTHORIZATION.name()) // Remove existing auth header if any
                                .addHeader(HttpHeader.AUTHORIZATION.name(), "O-Bearer " + config.getOpenai().getApiKey())
                                .build();
                        log.debug("Outgoing request: {} {}", newRequest.method(), newRequest.url());
                        log.debug("Headers: {}", newRequest.headers());
                        log.debug("Auth header: {}", newRequest.header(HttpHeader.AUTHORIZATION.name()));
                        if (newRequest.body() != null) {
                            log.trace("Body: {}", newRequest.body());
                        }
                        return chain
                                .proceed(newRequest);
                    }
                })
                .addNetworkInterceptor(new Interceptor() {
                    @Override
                    public @NonNull Response intercept(Interceptor.Chain chain) throws IOException {
                        log.debug("Outgoing request: {} {}", chain.request().method(), chain.request().url());
                        log.debug("Headers: {}", chain.request().headers());
                        log.debug("Auth header: {}", chain.request().header(HttpHeader.AUTHORIZATION.name()));
                        if (chain.request().body() != null) {
                            log.trace("Body: {}", chain.request().body());
                        }
                        return chain.proceed(chain.request());
                    }
                })
                .build();
        OkHttpClientAdapter clientAdapter = new OkHttpClientAdapter(httpClient);

        // 6. Build model
        final var openAI = SimpleOpenAI.builder()
                .baseUrl(config.getOpenai().getBaseUrl())
                .apiKey(config.getOpenai().getApiKey())
                .objectMapper(new ObjectMapper())
                .clientAdapter(clientAdapter)
                .build();
        log.info("OpenAI model configured with baseURL: {}, modelName: {}",
                config.getOpenai().getBaseUrl(), config.getOpenai().getModel());
        final var model = new SimpleOpenAIModel<>(config.getOpenai().getModel(), openAI, mapper);

        // 7. Agent setup
        final var agentSetup = AgentSetup.builder()
                .mapper(mapper)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(config.getAgent().getTemperature())
                        .maxTokens(config.getAgent().getMaxTokens())
                        .build())
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool((result) -> {
                    try {
                        SqlQueryResult typedResult = mapper.readValue(result, SqlQueryResult.class);
                        return LocalSqlTools.formatResultsAsTable(typedResult);
                    } catch (Exception e) {
                        throw new RuntimeException("Model didn't produce a JSON document of type SQLQueryResult. Got: " + result, e);
                    }
                })
                .build();

        // 8. Skills extension — extract bundled skill to temp dir if no explicit path given
        final String resolvedSkillsDir = resolveSkillsDir();
        final var skillsExtension = AgentSkillsExtension
                .<String, SqlQueryResult, TextToSqlAgent>withMultipleSkills()
                .baseDir(resolvedSkillsDir)
                .skillsDirectories(List.of("."))
                .build();

        // 9. Build agent
        final TextToSqlAgent agent = TextToSqlAgent.builder()
                .setup(agentSetup)
                .extension(skillsExtension)
                .build();

        // 10. Register local tools (timezone conversion, schema, formatting)
        agent.registerTools(ToolUtils.readTools(new LocalSqlTools(dbPath
                .toString())));

        // 11. Register remote-HTTP toolbox pointing at the embedded Dropwizard server
        final var toolSource = new InMemoryHttpToolSource();
        final var httpToolsResource = getClass().getResourceAsStream("/http-tools/sqlite-api.yml");
        Objects.requireNonNull(httpToolsResource, "Bundled http-tools/sqlite-api.yml not found on classpath");
        HttpToolReaders.loadToolsFromYAMLContent(httpToolsResource.readAllBytes(), toolSource);
        final var httpToolBox = new HttpToolBox("sqlite-api",
                                                new OkHttpClient.Builder().build(),
                                                toolSource,
                                                mapper,
                                                baseUrl);
        agent.registerToolbox(httpToolBox);

        // 12. Interactive prompt loop
        printBanner();
        final var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            line = console.readLine();
            if (line == null) {
                // EOF (Ctrl+D / pipe end)
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;
            }

            final String question = line;
            final long startMs = System.currentTimeMillis();

            try {
                if (config.getAgent().isStreaming()) {
                    // Stream text tokens to stdout as they arrive
                    System.out.println();
                    final var outputFuture = agent.executeAsyncTextStreaming(
                                                                             AgentInput.<String>builder()
                                                                                     .request(question)
                                                                                     .requestMetadata(AgentRequestMetadata
                                                                                             .builder()
                                                                                             .sessionId(effectiveSessionId)
                                                                                             .userId("cli-user")
                                                                                             .build())
                                                                                     .build(),
                                                                             chunk -> System.out.print(new String(chunk,
                                                                                                                  StandardCharsets.UTF_8)));
                    final var output = outputFuture.join();
                    System.out.println();
                    if (output.getData() == null && output.getError() != null) {
                        System.err.println("[Error] " + output.getError().getMessage());
                    }
                }
                else {
                    // Non-streaming: wait for full structured result
                    final var output = agent.executeAsync(
                                                          AgentInput.<String>builder()
                                                                  .request(question)
                                                                  .requestMetadata(AgentRequestMetadata.builder()
                                                                          .sessionId(effectiveSessionId)
                                                                          .userId("cli-user")
                                                                          .build())
                                                                  .build()).join();

                    if (output.getError() != null) {
                        System.err.println("[Error] " + output.getError().getMessage());
                    }
                    else {
                        final SqlQueryResult result = output.getData();
                        printStructuredResult(result, System.currentTimeMillis() - startMs);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("[Error] " + e.getMessage());
                log.error("Agent execution failed", e);
            }
        }

        return 0;
    }

    /**
     * Resolves the skills directory.
     *
     * <p>If {@code --skills-dir} was provided, use it directly.
     * Otherwise, extract the bundled {@code /skills/} resource tree to a temporary
     * directory so that {@code AgentSkillsExtension} can discover it on the filesystem.
     */
    @SneakyThrows
    private String resolveSkillsDir() {
        if (skillsDir != null && !skillsDir.isBlank()) {
            return skillsDir;
        }

        // Extract the bundled skill directory to a temp location
        final Path tempSkillsDir = Files.createTempDirectory("sentinel-ai-skills-");
        tempSkillsDir.toFile().deleteOnExit();

        // The skill lives at resources/skills/sql-execution/SKILL.md
        final Path skillDir = tempSkillsDir.resolve("sql-execution");
        Files.createDirectories(skillDir);

        final var skillStream = getClass().getResourceAsStream("/skills/sql-execution/SKILL.md");
        if (skillStream == null) {
            log.warn("Bundled skill SKILL.md not found on classpath — skills extension may have no skills");
        }
        else {
            final Path skillFile = skillDir.resolve("SKILL.md");
            Files.write(skillFile, skillStream.readAllBytes());
            log.info("Extracted bundled skill to {}", skillFile);
        }

        return tempSkillsDir.toAbsolutePath().toString();
    }
}
