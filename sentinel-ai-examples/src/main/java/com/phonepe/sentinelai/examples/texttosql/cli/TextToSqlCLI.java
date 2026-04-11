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
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer;
import com.phonepe.sentinelai.examples.texttosql.server.SqliteRestServer;
import com.phonepe.sentinelai.examples.texttosql.tools.AskUserTool;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import com.phonepe.sentinelai.examples.texttosql.tools.LocalTools;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPSSEServerConfig;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPStdioServerConfig;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
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

/**
 * Entry point for the Text-to-SQL interactive CLI.
 *
 * <p>Run with:
 *
 * <pre>
 * java -jar sentinel-ai-examples-cli.jar [options]
 * </pre>
 *
 * <p>On startup the CLI:
 *
 * <ol>
 *   <li>Reads the YAML config file ({@code .env/agent-config.yml} by default).
 *   <li>Initialises the SQLite database (schema + sample data) if it does not exist.
 *   <li>Starts the embedded Dropwizard SQLite REST server on a free port.
 *   <li>Extracts the bundled SQL-execution skill to a temp directory.
 *   <li>Builds the {@link TextToSqlAgent} with all toolboxes and the skills extension.
 *   <li>Enters an interactive prompt loop, streaming agent responses token-by-token.
 * </ol>
 */
@Slf4j
@Command(
        name = "text-to-sql",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Interactive Text-to-SQL agent powered by Sentinel AI")
public class TextToSqlCLI implements Callable<Integer> {

    /**
     * Selects the toolbox strategy used by the agent to execute SQL queries.
     *
     * <ul>
     *   <li>{@code HTTP} – embedded Dropwizard REST server accessed via the remote-HTTP toolbox
     *       (default)
     *   <li>{@code MCP} – MCP server launched as a subprocess, accessed via the MCP toolbox
     * </ul>
     */
    public enum ToolboxMode {
        HTTP,
        MCP
    }

    /**
     * Selects the transport used when the MCP toolbox is active ({@code --toolbox-mode MCP}).
     *
     * <ul>
     *   <li>{@code STDIO} – MCP messages are exchanged via stdin/stdout (default)
     *   <li>{@code SSE} – MCP messages are exchanged over HTTP Server-Sent Events; the subprocess
     *       binds to {@code --mcp-port}
     * </ul>
     */
    public enum McpServerMode {
        STDIO,
        SSE
    }

    /** Default port for the MCP SSE server subprocess. */
    public static final int DEFAULT_MCP_SSE_PORT = SqliteMcpServer.DEFAULT_SSE_PORT;

    @Option(
            names = {"--config", "-c"},
            description = "Path to credentials YAML file (default: .env/agent-config.yml)",
            defaultValue = ".env/agent-config.yml")
    private String configPath;

    @Option(
            names = {"--skills-dir", "-s"},
            description =
                    "Path to skills directory. If not set, the bundled skill is extracted to a temp dir.")
    private String skillsDir;

    @Option(
            names = {"--session-id"},
            description = "Session ID for conversation history (default: random UUID)",
            defaultValue = "")
    private String sessionId;

    @Option(
            names = {"--toolbox-mode", "-t"},
            description =
                    "Toolbox to use for SQL execution: ${COMPLETION-CANDIDATES} "
                            + "(HTTP = embedded REST server, MCP = stdio MCP server). Default: HTTP",
            defaultValue = "HTTP")
    private ToolboxMode toolboxMode;

    @Option(
            names = {"--mcp-server-mode"},
            description =
                    "Transport mode for the MCP server subprocess: ${COMPLETION-CANDIDATES} "
                            + "(only used when --toolbox-mode is MCP). Default: STDIO",
            defaultValue = "STDIO")
    private McpServerMode mcpServerMode;

    @Option(
            names = {"--mcp-port"},
            description =
                    "HTTP port for the MCP SSE server subprocess "
                            + "(only used when --mcp-server-mode is SSE, default: "
                            + DEFAULT_MCP_SSE_PORT
                            + ")",
            defaultValue = "" + DEFAULT_MCP_SSE_PORT)
    private int mcpPort;

    /**
     * Caches the most recent {@link AgentOutput} so that {@code /dumpMessages} can export it at any
     * point during the interactive session.
     */
    private AgentOutput<SqlQueryResult> lastAgentOutput = null;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        System.exit(new CommandLine(new TextToSqlCLI()).execute(args));
    }

    // -------------------------------------------------------------------------
    // Callable implementation — clean orchestration
    // -------------------------------------------------------------------------

    @Override
    @SneakyThrows
    public Integer call() {
        log.info("Starting Text-to-SQL CLI initialisation [toolboxMode={}]", toolboxMode);

        final CliConfig config = loadConfig(configPath);
        validateConfig(config);

        final String effectiveSessionId = resolveSessionId(sessionId);
        final ObjectMapper mapper = JsonUtils.createMapper();

        final Path dbPath = initializeDatabase(config);

        final OkHttpClientAdapter clientAdapter = buildTrustedHttpClient(config);
        final SimpleOpenAIModel<?> model = buildOpenAIModel(config, clientAdapter, mapper);
        final AgentSetup agentSetup = buildAgentSetup(config, model, mapper);

        final AgentSkillsExtension<String, SqlQueryResult, TextToSqlAgent> skillsExtension =
                buildSkillsExtension();
        final TextToSqlAgent agent = buildAgent(agentSetup, skillsExtension);

        registerLocalTools(agent, dbPath);
        registerAskUserTool(agent);

        if (toolboxMode == ToolboxMode.MCP) {
            if (mcpServerMode == McpServerMode.SSE) {
                log.info("Registering MCP toolbox (SSE subprocess on port {})", mcpPort);
                registerMcpToolboxSse(agent, dbPath, mapper, mcpPort);
            } else {
                log.info("Registering MCP toolbox (stdio subprocess)");
                registerMcpToolbox(agent, dbPath, mapper);
            }
        } else {
            log.info("Registering HTTP toolbox (embedded REST server)");
            final String baseUrl = startRestServer(dbPath, mapper);
            registerHttpToolbox(agent, baseUrl, mapper);
        }

        log.info(
                "Initialisation complete — starting interactive session [sessionId={}, toolboxMode={}, mcpServerMode={}]",
                effectiveSessionId,
                toolboxMode,
                toolboxMode == ToolboxMode.MCP ? mcpServerMode : "N/A");
        ConsoleUtils.printBanner();
        ConsoleUtils.printExamples();

        return runInteractiveLoop(agent, config, effectiveSessionId, mapper);
    }

    // -------------------------------------------------------------------------
    // Initialisation steps
    // -------------------------------------------------------------------------

    /**
     * Resolves the effective session ID, generating a random UUID when the caller did not supply
     * one.
     */
    private static String resolveSessionId(String sessionId) {
        final String resolved =
                (sessionId == null || sessionId.isBlank())
                        ? UUID.randomUUID().toString()
                        : sessionId;
        log.info("Session ID resolved to: {}", resolved);
        return resolved;
    }

    /**
     * Ensures the SQLite database exists and is seeded with schema + sample data.
     *
     * @return absolute path to the database file
     */
    @SneakyThrows
    private static Path initializeDatabase(CliConfig config) {
        final Path dbPath = Paths.get(config.getDatabase().getPath()).toAbsolutePath();
        log.info("Initialising database at {}", dbPath);
        DatabaseInitializer.ensureInitialised(dbPath);
        log.info("Database initialised successfully at {}", dbPath);
        return dbPath;
    }

    /**
     * Starts the embedded Dropwizard SQLite REST server on a dynamically selected free port.
     *
     * @return the base URL of the running server (e.g. {@code http://localhost:12345})
     */
    @SneakyThrows
    private static String startRestServer(Path dbPath, ObjectMapper mapper) {
        log.info("Starting embedded SQLite REST server for database {}", dbPath);
        final int port = SqliteRestServer.findFreePort();
        final String baseUrl = SqliteRestServer.startEmbedded(dbPath.toString(), port, mapper);
        log.info("SQLite REST server ready at {}", baseUrl);
        return baseUrl;
    }

    /**
     * Builds an {@link OkHttpClientAdapter} backed by the system default SSL context and
     * interceptors that inject the OpenAI authorization header on every outgoing request.
     */
    @SneakyThrows
    private static OkHttpClientAdapter buildTrustedHttpClient(CliConfig config) {
        log.info("Building HTTP client");

        final String apiKey = config.getOpenai().getApiKey();
        final OkHttpClient httpClient =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                new Interceptor() {
                                    @Override
                                    public @NonNull Response intercept(Interceptor.Chain chain)
                                            throws IOException {
                                        log.debug(
                                                "Incoming request: {} {}",
                                                chain.request().method(),
                                                chain.request().url());
                                        log.debug(
                                                "Auth header: {}",
                                                chain.request()
                                                        .header(HttpHeader.AUTHORIZATION.name()));
                                        val newRequest =
                                                chain.request()
                                                        .newBuilder()
                                                        .removeHeader(
                                                                HttpHeader.AUTHORIZATION.name())
                                                        .addHeader(
                                                                HttpHeader.AUTHORIZATION.name(),
                                                                config.getOpenai().getBearerPrefix() + apiKey)
                                                        .build();
                                        log.debug(
                                                "Outgoing request: {} {}",
                                                newRequest.method(),
                                                newRequest.url());
                                        return chain.proceed(newRequest);
                                    }
                                })
                        .build();

        log.info("HTTP client built successfully");
        return new OkHttpClientAdapter(httpClient);
    }

    /** Constructs the {@link SimpleOpenAIModel} wired to the configured model name and base URL. */
    private static SimpleOpenAIModel<?> buildOpenAIModel(
            CliConfig config, OkHttpClientAdapter clientAdapter, ObjectMapper mapper) {
        log.info(
                "Building OpenAI model [name={}, baseUrl={}]",
                config.getOpenai().getModel(),
                config.getOpenai().getBaseUrl());
        final SimpleOpenAI openAI =
                SimpleOpenAI.builder()
                        .baseUrl(config.getOpenai().getBaseUrl())
                        .apiKey(config.getOpenai().getApiKey())
                        .objectMapper(new ObjectMapper())
                        .clientAdapter(clientAdapter)
                        .build();
        log.info("OpenAI model built successfully");
        return new SimpleOpenAIModel<>(config.getOpenai().getModel(), openAI, mapper);
    }

    /**
     * Creates the {@link AgentSetup} that controls model behaviour (temperature, max tokens, output
     * mode).
     */
    private static AgentSetup buildAgentSetup(
            CliConfig config, SimpleOpenAIModel<?> model, ObjectMapper mapper) {
        log.info(
                "Configuring agent setup [temperature={}, maxTokens={}, streaming={}]",
                config.getAgent().getTemperature(),
                config.getAgent().getMaxTokens(),
                config.getAgent().isStreaming());
        return AgentSetup.builder()
                .mapper(mapper)
                .model(model)
                .modelSettings(
                        ModelSettings.builder()
                                .temperature(config.getAgent().getTemperature())
                                .maxTokens(config.getAgent().getMaxTokens())
                                .build())
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(result -> result)
                .build();
    }

    /**
     * Builds and configures the {@link AgentSkillsExtension}, extracting the bundled skill to a
     * temporary directory when no explicit skills directory was supplied.
     */
    @SneakyThrows
    private AgentSkillsExtension<String, SqlQueryResult, TextToSqlAgent> buildSkillsExtension() {
        final String resolvedSkillsDir = resolveSkillsDir();
        log.info("Building skills extension from directory: {}", resolvedSkillsDir);
        return AgentSkillsExtension.<String, SqlQueryResult, TextToSqlAgent>withMultipleSkills()
                .baseDir(".")
                .skillsDirectories(List.of(resolvedSkillsDir))
                .build();
    }

    /** Instantiates the {@link TextToSqlAgent} with the provided setup and skills extension. */
    private static TextToSqlAgent buildAgent(
            AgentSetup agentSetup,
            AgentSkillsExtension<String, SqlQueryResult, TextToSqlAgent> skillsExtension) {
        log.info("Building Text-to-SQL agent");
        final TextToSqlAgent agent =
                TextToSqlAgent.builder()
                        .setup(agentSetup)
                        .extension(skillsExtension)
                        .outputValidator(
                                (context, agentOutput) -> OutputValidationResults.success())
                        .build();
        log.info("Text-to-SQL agent built successfully");
        return agent;
    }

    /**
     * Registers the {@link AskUserTool} with the agent.
     *
     * <p>This tool enables the LLM to pause and ask the human operator for clarification whenever
     * the user's prompt is ambiguous, incomplete, or admits multiple valid interpretations. The
     * agent can either ask a free-form question ({@code ask_user_question}) or present a numbered
     * list of choices ({@code ask_user_to_choose}).
     */
    private static void registerAskUserTool(TextToSqlAgent agent) {
        log.info("Registering ask-user tool");
        agent.registerToolbox(new AskUserTool());
        log.info("Ask-user tool registered successfully");
    }

    /**
     * Registers the local SQL tools (timezone conversion, schema introspection, result formatting,
     * and hybrid schema search) with the agent.
     *
     * <p>The {@link LocalTools} constructor also initialises the Lucene schema vector store
     * under {@code {dataDir}/lucene-schema-index/}, building the index on first run.
     */
    @SneakyThrows
    private static void registerLocalTools(TextToSqlAgent agent, Path dbPath) {
        final Path dataDir = dbPath.getParent();
        log.info("Registering local SQL tools for database: {} (dataDir: {})", dbPath, dataDir);
        agent.registerTools(ToolUtils.readTools(new LocalTools(dbPath.toString(), dataDir)));
        log.info("Local SQL tools registered successfully");
    }

    /**
     * Loads the bundled HTTP tool definitions and registers the remote-HTTP toolbox pointing at the
     * embedded Dropwizard server.
     */
    @SneakyThrows
    private static void registerHttpToolbox(
            TextToSqlAgent agent, String baseUrl, ObjectMapper mapper) {
        log.info("Registering HTTP toolbox at base URL: {}", baseUrl);
        final var toolSource = new InMemoryHttpToolSource();
        final var httpToolsResource =
                TextToSqlCLI.class.getResourceAsStream("/http-tools/sqlite-api.yml");
        Objects.requireNonNull(
                httpToolsResource, "Bundled http-tools/sqlite-api.yml not found on classpath");
        HttpToolReaders.loadToolsFromYAMLContent(httpToolsResource.readAllBytes(), toolSource);
        final var httpToolBox =
                new HttpToolBox(
                        "sqlite-api",
                        new OkHttpClient.Builder().build(),
                        toolSource,
                        mapper,
                        baseUrl);
        agent.registerToolbox(httpToolBox);
        log.info("HTTP toolbox registered successfully");
    }

    /**
     * Registers an MCP toolbox that launches {@code SqliteMcpServer} as a subprocess and
     * communicates with it via the MCP stdio protocol.
     *
     * <p>The subprocess is started using the current JVM executable and the running classpath, so
     * no separate JAR distribution is required.
     */
    private static void registerMcpToolbox(TextToSqlAgent agent, Path dbPath, ObjectMapper mapper) {
        final String javaCmd = ProcessHandle.current().info().command().orElse("java");
        final String classpath = System.getProperty("java.class.path");
        log.info("Registering MCP toolbox [javaCmd={}, dbPath={}]", javaCmd, dbPath);

        final var mcpConfig =
                MCPStdioServerConfig.builder()
                        .command(javaCmd)
                        .args(
                                List.of(
                                        "-cp",
                                        classpath,
                                        "com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer",
                                        "--db-path",
                                        dbPath.toString()))
                        .build();

        final var mcpToolBox =
                MCPToolBox.buildFromConfig()
                        .name("sqlite-mcp")
                        .mapper(mapper)
                        .mcpServerConfig(mcpConfig)
                        .build();

        agent.registerToolbox(mcpToolBox);
        log.info("MCP toolbox registered successfully");
    }

    /**
     * Registers an MCP toolbox that launches {@code SqliteMcpServer} as a subprocess using SSE
     * transport and connects to it via the MCP SSE client.
     *
     * <p>The subprocess binds an embedded Jetty HTTP server on {@code port} and serves the MCP SSE
     * endpoint at {@code http://localhost:<port>/sse}. The CLI polls the port until the server is
     * ready before registering the toolbox.
     */
    @SneakyThrows
    private static void registerMcpToolboxSse(
            TextToSqlAgent agent, Path dbPath, ObjectMapper mapper, int port) {
        final String javaCmd = ProcessHandle.current().info().command().orElse("java");
        final String classpath = System.getProperty("java.class.path");
        log.info(
                "Launching MCP SSE server subprocess [javaCmd={}, dbPath={}, port={}]",
                javaCmd,
                dbPath,
                port);

        final var pb =
                new ProcessBuilder(
                        javaCmd,
                        "-cp",
                        classpath,
                        "com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer",
                        "--db-path",
                        dbPath.toString(),
                        "--transport",
                        "SSE",
                        "--port",
                        String.valueOf(port));
        // Inherit stderr so MCP server logs appear in the parent process's stderr stream
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.start(); // subprocess runs in background; no handle needed

        log.info("Waiting for MCP SSE server to become available on port {}", port);
        waitForMcpSseServer("localhost", port, 30_000);
        log.info("MCP SSE server ready at http://localhost:{}/sse", port);

        final var mcpConfig = MCPSSEServerConfig.builder().url("http://localhost:" + port).build();

        final var mcpToolBox =
                MCPToolBox.buildFromConfig()
                        .name("sqlite-mcp")
                        .mapper(mapper)
                        .mcpServerConfig(mcpConfig)
                        .build();

        agent.registerToolbox(mcpToolBox);
        log.info("MCP SSE toolbox registered successfully");
    }

    /**
     * Polls {@code host:port} until a TCP connection succeeds or the timeout elapses, then returns.
     * Used to wait for the MCP SSE subprocess to bind its listening socket before the client
     * attempts to connect.
     *
     * @throws IllegalStateException if the port does not become reachable within {@code timeoutMs}
     */
    @SneakyThrows
    private static void waitForMcpSseServer(String host, int port, long timeoutMs)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 500);
                return; // connected - server is ready
            } catch (IOException ignored) {
                // Connection not yet available; wait and retry
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException(
                "MCP SSE server did not start on port " + port + " within " + timeoutMs + " ms");
    }

    // -------------------------------------------------------------------------
    // Interactive prompt loop
    // -------------------------------------------------------------------------

    /**
     * Runs the read-eval-print loop until the user types {@code exit}/{@code quit} or EOF is
     * reached.
     *
     * @return exit code ({@code 0} = success)
     */
    @SneakyThrows
    private int runInteractiveLoop(
            TextToSqlAgent agent,
            CliConfig config,
            String effectiveSessionId,
            ObjectMapper mapper) {
        final var console =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while (true) {
            ConsoleUtils.printPrompt();
            line = console.readLine();
            if (line == null) {
                System.out.println("EOF encountered. Exiting!");
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                ConsoleUtils.printWarning("Empty input — please provide a prompt.");
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;
            }
            if (line.startsWith("/dumpMessages")) {
                // output the message exchange thus far with the agent (based on the last agent
                // output)
                // the messages are written in JSON format for debugging purpose to a file in the
                // current directory
                final String[] parts = line.split("\\s+", 2);
                final String filename =
                        parts.length > 1 && !parts[1].isBlank()
                                ? parts[1].trim()
                                : "messages-" + System.currentTimeMillis() + ".json";
                dumpMessages(filename, mapper);
                continue;
            }
            handleQuery(agent, config, line, effectiveSessionId);
        }
        return 0;
    }

    /**
     * Dispatches a single user query to the agent in either streaming or non-streaming mode,
     * depending on the configuration, and prints the result.
     */
    private void handleQuery(
            TextToSqlAgent agent, CliConfig config, String question, String effectiveSessionId) {
        final long startMs = System.currentTimeMillis();
        log.debug(
                "Handling query [streaming={}, sessionId={}]: {}",
                config.getAgent().isStreaming(),
                effectiveSessionId,
                question);
        try {
            val agentInput =
                    AgentInput.<String>builder()
                            .request(question)
                            .requestMetadata(
                                    AgentRequestMetadata.builder()
                                            .sessionId(effectiveSessionId)
                                            .userId("cli-user")
                                            .build())
                            .build();
            CompletableFuture<AgentOutput<SqlQueryResult>> outputFuture;
            if (config.getAgent().isStreaming()) {
                System.out.println();
                outputFuture =
                        agent.executeAsyncStreaming(
                                agentInput,
                                chunk ->
                                        System.out.print(
                                                new String(chunk, StandardCharsets.UTF_8)));
            } else {
                outputFuture = agent.executeAsync(agentInput);
            }
            final AgentOutput<SqlQueryResult> output =
                    ConsoleUtils.awaitWithSpinner(outputFuture, true);
            lastAgentOutput = output;
            output.getAllMessages();
            System.out.println();
            if (output.getData() != null) {
                ConsoleUtils.printStructuredResult(
                        output.getData(), System.currentTimeMillis() - startMs);
            } else if (output.getError() != null) {
                ConsoleUtils.printError(output.getError().getMessage());
            } else {
                ConsoleUtils.printWarning(
                        "Empty result from agent, something has gone wrong. Nothing to display");
            }
            ConsoleUtils.printUsageStats(output.getUsage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ConsoleUtils.printError("Query interrupted");
            log.warn("Agent execution interrupted for query: {}", question, e);
        } catch (Exception e) {
            ConsoleUtils.printError(e.getMessage());
            log.error("Agent execution failed for query: {}", question, e);
        }
    }

    /**
     * Serializes all {@link com.phonepe.sentinelai.core.agentmessages.AgentMessage AgentMessage}s
     * from the most recent {@link AgentOutput} to {@code filename} as a pretty-printed JSON array.
     *
     * <p>If no query has been executed yet, a warning is printed instead.
     *
     * @param filename path of the output file (created or overwritten)
     * @param mapper the Jackson {@link ObjectMapper} used for serialization
     */
    @SneakyThrows
    private void dumpMessages(String filename, ObjectMapper mapper) {
        if (lastAgentOutput == null) {
            ConsoleUtils.printWarning(
                    "No agent output cached yet — run a query first before using /dumpMessages.");
            return;
        }
        final var messages = lastAgentOutput.getAllMessages();
        final Path filePath = Path.of(filename);
        final Path finalOutputPath = Path.of(".logs").resolve(filePath);
        final String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
        Files.writeString(finalOutputPath, json, StandardCharsets.UTF_8);
        log.info("Dumped {} messages to {}", messages.size(), finalOutputPath.toAbsolutePath());
        ConsoleUtils.printDumpSuccess(finalOutputPath.toAbsolutePath().toString(), messages.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private static CliConfig loadConfig(String configPath) {
        final Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            System.err.println("Config file not found: " + path.toAbsolutePath());
            System.err.println(
                    "Copy src/main/resources/.env/agent-config.yml.example to "
                            + path.toAbsolutePath()
                            + " and fill in your API key.");
            System.exit(1);
        }
        log.info("Loading config from {}", path.toAbsolutePath());
        final var yamlMapper = new YAMLMapper();
        return yamlMapper.readValue(path.toFile(), CliConfig.class);
    }

    private static void validateConfig(CliConfig config) {
        if (config.getOpenai().getApiKey() == null || config.getOpenai().getApiKey().isBlank()) {
            System.err.println("Error: openai.apiKey must be set in the credentials file.");
            System.exit(1);
        }
    }

    /**
     * Resolves the skills directory.
     *
     * <p>If {@code --skills-dir} was provided, use it directly. Otherwise, extract the bundled
     * {@code /skills/} resource tree to a temporary directory so that {@code AgentSkillsExtension}
     * can discover it on the filesystem.
     */
    @SneakyThrows
    private String resolveSkillsDir() {
        if (skillsDir != null && !skillsDir.isBlank()) {
            log.info("Using explicitly provided skills directory: {}", skillsDir);
            return skillsDir;
        }

        log.info("No skills directory provided — extracting bundled skill to a temp directory");
        final Path tempSkillsDir = Files.createTempDirectory("sentinel-ai-skills-");
        tempSkillsDir.toFile().deleteOnExit();

        final Path skillDir = tempSkillsDir.resolve("sql-execution");
        Files.createDirectories(skillDir);

        final var skillStream = getClass().getResourceAsStream("/skills/sql-execution/SKILL.md");
        if (skillStream == null) {
            log.warn(
                    "Bundled skill SKILL.md not found on classpath — skills extension may have no skills");
        } else {
            final Path skillFile = skillDir.resolve("SKILL.md");
            Files.write(skillFile, skillStream.readAllBytes());
            log.info("Extracted bundled skill to {}", skillFile);
        }

        return tempSkillsDir.toAbsolutePath().toString();
    }
}
