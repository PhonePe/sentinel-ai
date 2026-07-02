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
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.StreamConsumer;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtension;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the constants and enum types exposed by {@link TextToSqlCLI}.
 *
 * <p>The {@link TextToSqlCLI#call()} method requires a live OpenAI API key, an initialised SQLite
 * database, and several external services — these are covered by end-to-end / integration tests.
 * Here we focus on the public API surface that can be exercised without infrastructure.
 */
class TextToSqlCLITest {

    // =========================================================================
    // ToolboxMode enum
    // =========================================================================

    @Nested
    class BuildAgentSetupTests {

        @Test
        void returnsNonNullAgentSetup() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);

            final var mapper = JsonUtils.createMapper();
            final var clientAdapter = (OkHttpClientAdapter) invokeStaticMethod(
                                                                               "buildTrustedHttpClient",
                                                                               new Class<?>[]{
                                                                                       CliConfig.class
                                                                               },
                                                                               config);
            final var model = invokeStaticMethod(
                                                 "buildOpenAIModel",
                                                 new Class<?>[]{
                                                         CliConfig.class,
                                                         OkHttpClientAdapter.class,
                                                         ObjectMapper.class
                                                 },
                                                 config,
                                                 clientAdapter,
                                                 mapper);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "buildAgentSetup",
                                                               CliConfig.class,
                                                               com.phonepe.sentinelai.models.SimpleOpenAIModel.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);
            final var agentSetup = m.invoke(null, config, model, mapper);

            assertNotNull(agentSetup);
        }

        private Object invokeStaticMethod(String name, Class<?>[] paramTypes, Object... args)
                throws Exception {
            final var m = TextToSqlCLI.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        }
    }

    @Nested
    class BuildAgentTests {

        @Test
        void addsSkillsAndOpenTelemetryExtensions() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            final var buildClient = TextToSqlCLI.class.getDeclaredMethod(
                                                                         "buildTrustedHttpClient",
                                                                         CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var skillsExtension = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildOtel = TextToSqlCLI.class.getDeclaredMethod("buildOpenTelemetryExtension");
            buildOtel.setAccessible(true);
            final var otelExtension = buildOtel.invoke(null);

            final var buildAgent = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgent",
                                                                        AgentSetup.class,
                                                                        AgentSkillsExtension.class,
                                                                        OpenTelemetryAgentExtension.class);
            buildAgent.setAccessible(true);
            final var agent = (TextToSqlAgent) buildAgent.invoke(null,
                                                                 agentSetup,
                                                                 skillsExtension,
                                                                 otelExtension);

            final var extensionsField = agent.getClass().getSuperclass().getDeclaredField("extensions");
            extensionsField.setAccessible(true);
            @SuppressWarnings("unchecked") final var extensions = (List<Object>) extensionsField.get(agent);

            assertEquals(2, extensions.size());
            assertEquals("agent-skills", invokeName(extensions.get(0)));
            assertEquals("open-telemetry-agent-extension", invokeName(extensions.get(1)));
        }

        @Test
        void buildsAgentSuccessfully() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            // build prerequisites
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod(
                                                                         "buildTrustedHttpClient",
                                                                         CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            // build skills extension via instance method
            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var skillsExtension = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgent = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgent",
                                                                        AgentSetup.class,
                                                                        AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            final var agent = buildAgent.invoke(null, agentSetup, skillsExtension);

            assertNotNull(agent);
            assertInstanceOf(TextToSqlAgent.class, agent);
        }

        private String invokeName(Object extension) throws Exception {
            final var nameMethod = extension.getClass().getDeclaredMethod("name");
            nameMethod.setAccessible(true);
            return (String) nameMethod.invoke(extension);
        }
    }

    // =========================================================================
    // McpServerMode enum
    // =========================================================================

    @Nested
    class BuildOpenAIModelTests {

        @Test
        void returnsNonNullModel() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");

            final var mapper = JsonUtils.createMapper();
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod(
                                                                         "buildTrustedHttpClient",
                                                                         CliConfig.class);
            buildClient.setAccessible(true);
            final var clientAdapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "buildOpenAIModel",
                                                               CliConfig.class,
                                                               OkHttpClientAdapter.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);
            final var model = m.invoke(null, config, clientAdapter, mapper);

            assertNotNull(model);
            assertInstanceOf(SimpleOpenAIModel.class, model);
        }
    }

    @Nested
    @WireMockTest
    class BuildTrustedHttpClientInterceptorTests {

        @Test
        void interceptorInjectsBearerToken(WireMockRuntimeInfo wmInfo) throws Exception {
            // Arrange: stub WireMock to accept any GET and return 200
            stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200).withBody("ok")));

            final var config = new CliConfig();
            config.getOpenai().setApiKey("secret-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("http://localhost:" + wmInfo.getHttpPort());

            // Build the adapter via reflection (static method)
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            // Extract the private OkHttpClient from the adapter via reflection
            final var okHttpClientField = OkHttpClientAdapter.class.getDeclaredField("okHttpClient");
            okHttpClientField.setAccessible(true);
            final var okHttpClient = (OkHttpClient) okHttpClientField.get(adapter);

            // Make a real HTTP call to WireMock — this triggers the interceptor
            final var request = new Request.Builder()
                    .url("http://localhost:" + wmInfo.getHttpPort() + "/test")
                    .addHeader("Authorization", "old-value")
                    .build();
            try (var response = okHttpClient.newCall(request).execute()) {
                assertEquals(200, response.code());
            }

            // Verify WireMock received the rewritten Authorization header
            verify(
                   getRequestedFor(urlEqualTo("/test"))
                           .withHeader(
                                       "Authorization",
                                       equalTo(
                                               config.getOpenai().getBearerPrefix()
                                                       + "secret-key")));
        }
    }

    // =========================================================================
    // DEFAULT_MCP_SSE_PORT constant
    // =========================================================================

    @Nested
    class BuildTrustedHttpClientTests {

        @Test
        void returnsNonNullAdapter() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setBearerPrefix("Bearer ");

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "buildTrustedHttpClient",
                                                               CliConfig.class);
            m.setAccessible(true);
            final var result = m.invoke(null, config);

            assertNotNull(result);
            assertInstanceOf(OkHttpClientAdapter.class, result);
        }
    }

    // =========================================================================
    // Constructor / instantiation
    // =========================================================================

    @Nested
    class ConstantsAndEnumsTests {

        @Test
        void canBeInstantiatedWithoutThrowing() {
            assertDoesNotThrow(TextToSqlCLI::new);
        }

        @Test
        void defaultMcpSsePortMatchesSqliteMcpServerDefault() {
            assertEquals(SqliteMcpServer.DEFAULT_SSE_PORT, TextToSqlCLI.DEFAULT_MCP_SSE_PORT);
        }

        @Test
        void mcpServerModeEnumValues() {
            final var values = TextToSqlCLI.McpServerMode.values();
            assertEquals(2, values.length);
            assertEquals(TextToSqlCLI.McpServerMode.STDIO, values[0]);
            assertEquals(TextToSqlCLI.McpServerMode.SSE, values[1]);
        }

        @Test
        void mcpServerModeValueOf() {
            assertEquals(
                         TextToSqlCLI.McpServerMode.STDIO,
                         TextToSqlCLI.McpServerMode.valueOf("STDIO"));
            assertEquals(TextToSqlCLI.McpServerMode.SSE, TextToSqlCLI.McpServerMode.valueOf("SSE"));
        }

        @Test
        void toolboxModeEnumValues() {
            final var values = TextToSqlCLI.ToolboxMode.values();
            assertEquals(2, values.length);
            assertEquals(TextToSqlCLI.ToolboxMode.HTTP, values[0]);
            assertEquals(TextToSqlCLI.ToolboxMode.MCP, values[1]);
        }

        @Test
        void toolboxModeValueOf() {
            assertEquals(TextToSqlCLI.ToolboxMode.HTTP, TextToSqlCLI.ToolboxMode.valueOf("HTTP"));
            assertEquals(TextToSqlCLI.ToolboxMode.MCP, TextToSqlCLI.ToolboxMode.valueOf("MCP"));
        }
    }

    // =========================================================================
    // resolveSessionId (via reflection)
    // =========================================================================

    @Nested
    class DumpMessagesTests {

        @Test
        void printsWarningWhenNoOutput() throws Exception {
            final var cli = new TextToSqlCLI();
            // lastAgentOutput is null by default — exercising the early-return branch
            final var mapper = JsonUtils.createMapper();

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "dumpMessages",
                                                               String.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);
            // Should not throw; just prints a warning
            assertDoesNotThrow(() -> m.invoke(cli, "messages-test.json", mapper));
        }

        @Test
        void writesMessagesToLogsDirectory() throws Exception {
            // Ensure .logs/ directory exists (it's created by the production code)
            final var logsDir = Path.of(".logs");
            Files.createDirectories(logsDir);

            final var cli = new TextToSqlCLI();
            final var mapper = JsonUtils.createMapper();

            // Inject a non-null AgentOutput via reflection
            final AgentOutput<SqlQueryResult> output = new AgentOutput<>(null,
                                                                         List.of(),
                                                                         List.of(),
                                                                         null,
                                                                         null);
            final var lastOutputField = TextToSqlCLI.class.getDeclaredField("lastAgentOutput");
            lastOutputField.setAccessible(true);
            lastOutputField.set(cli, output);

            final var filename = "test-dump-" + System.nanoTime() + ".json";
            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "dumpMessages",
                                                               String.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(cli, filename, mapper));

            // Verify the file was written
            assertTrue(Files.exists(logsDir.resolve(filename)),
                       "Output file should exist in .logs/");
        }
    }

    // =========================================================================
    // resolveSkillsDir (via reflection)
    // =========================================================================

    @Nested
    class HandleQueryTests {

        private static final String SESSION = "test-session";

        @Test
        void exceptionIsCaughtAndPrinted() {
            final var agent = mock(TextToSqlAgent.class);
            when(agent.executeAsync(any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

            // Should NOT throw — exceptions are caught inside handleQuery
            assertDoesNotThrow(() -> invokeHandleQuery(new TextToSqlCLI(), agent, buildConfig(false), "fail query"));
        }

        @Test
        void nonStreamingErrorResult() {
            final var agent = mock(TextToSqlAgent.class);
            final var err = SentinelError.error(ErrorType.NO_RESPONSE);
            final AgentOutput<SqlQueryResult> output = new AgentOutput<>(null, List.of(), List.of(), null, err);
            when(agent.executeAsync(any()))
                    .thenReturn(CompletableFuture.completedFuture(output));

            assertDoesNotThrow(() -> invokeHandleQuery(new TextToSqlCLI(), agent, buildConfig(false), "bad query"));
        }

        @Test
        void nonStreamingSuccessWithData() {
            final var agent = mock(TextToSqlAgent.class);
            final var result = new SqlQueryResult("SELECT 1", List.of("{\"x\":1}"), "one row", 42L);
            final var output = new AgentOutput<>(result, List.of(), List.of(), null, null);
            when(agent.executeAsync(any()))
                    .thenReturn(CompletableFuture.completedFuture(output));

            assertDoesNotThrow(() -> invokeHandleQuery(new TextToSqlCLI(), agent, buildConfig(false), "show tables"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void streamingSuccessWithData() {
            final var agent = mock(TextToSqlAgent.class);
            final var result = new SqlQueryResult("SELECT 2", List.of(), "no rows", 10L);
            final var output = new AgentOutput<>(result, List.of(), List.of(), null, null);
            when(agent.executeAsyncStreaming(any(), any(StreamConsumer.class)))
                    .thenReturn(CompletableFuture.completedFuture(output));

            assertDoesNotThrow(() -> invokeHandleQuery(new TextToSqlCLI(), agent, buildConfig(true), "stream me"));
        }

        private CliConfig buildConfig(boolean streaming) {
            final var cfg = new CliConfig();
            cfg.getOpenai().setApiKey("test-key");
            cfg.getOpenai().setModel("gpt-4o");
            cfg.getOpenai().setBaseUrl("https://api.openai.com/v1");
            cfg.getAgent().setTemperature(0.0f);
            cfg.getAgent().setMaxTokens(4096);
            cfg.getAgent().setStreaming(streaming);
            return cfg;
        }

        /** Reflectively invokes the private {@code handleQuery} method. */
        private void invokeHandleQuery(
                                       TextToSqlCLI cli,
                                       TextToSqlAgent agent,
                                       CliConfig config,
                                       String question)
                throws Exception {
            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "handleQuery",
                                                               TextToSqlAgent.class,
                                                               CliConfig.class,
                                                               String.class,
                                                               String.class);
            m.setAccessible(true);
            try {
                m.invoke(cli, agent, config, question, SESSION);
            }
            catch (InvocationTargetException e) {
                // unwrap — let assertion helpers see the real exception if needed
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }
    }

    // =========================================================================
    // loadConfig (via reflection) — success and missing-file branches
    // =========================================================================

    @Nested
    class InitializeDatabaseTests {

        @TempDir
        Path tempDir;

        @Test
        void initialisesDatabase() throws Exception {
            final var dbFile = tempDir.resolve("test.db");
            final var config = new CliConfig();
            config.getDatabase().setPath(dbFile.toAbsolutePath().toString());

            final var m = TextToSqlCLI.class.getDeclaredMethod("initializeDatabase", CliConfig.class);
            m.setAccessible(true);
            final var result = (Path) m.invoke(null, config);

            assertNotNull(result);
            assertTrue(result.isAbsolute(), "Returned path must be absolute");
            assertTrue(Files.exists(result), "Database file must exist after initialisation");
        }
    }

    // =========================================================================
    // initializeDatabase (via reflection)
    // =========================================================================

    @Nested
    class LambdaCoverageTests {

        @Test
        void outputGenerationToolLambdaReturnsInput() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            // Directly invoke the outputGenerationTool lambda
            final var toolField = AgentSetup.class.getDeclaredField("outputGenerationTool");
            toolField.setAccessible(true);
            @SuppressWarnings("unchecked") final var tool = (UnaryOperator<String>) toolField.get(
                                                                                                  agentSetup);
            assertNotNull(tool);
            assertEquals("hello", tool.apply("hello"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void outputValidatorLambdaReturnsSuccess() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            final var agent = buildAgent(config, mapper);

            // Get the outputValidator field from Agent superclass
            final var validatorField = agent.getClass().getSuperclass().getDeclaredField("outputValidator");
            validatorField.setAccessible(true);
            final var validator = (OutputValidator<String, ?>) validatorField.get(agent);
            assertNotNull(validator);

            // Invoke with null context and null output — the lambda ignores both args
            final var result = validator.validate(null, null);
            assertNotNull(result);
            assertTrue(result.isSuccessful(), "outputValidator lambda should always return success");
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var skillsExtension = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgentMethod = TextToSqlCLI.class.getDeclaredMethod(
                                                                              "buildAgent",
                                                                              AgentSetup.class,
                                                                              AgentSkillsExtension.class);
            buildAgentMethod.setAccessible(true);
            return (TextToSqlAgent) buildAgentMethod.invoke(null, agentSetup, skillsExtension);
        }
    }

    // =========================================================================
    // buildTrustedHttpClient (via reflection)
    // =========================================================================

    @Nested
    class LoadConfigTests {

        @TempDir
        Path tempDir;

        @Test
        void loadsValidYamlConfig() throws Exception {
            final var configFile = tempDir.resolve("agent-config.yml");
            Files.writeString(
                              configFile,
                              """
                                      openai:
                                        apiKey: test-key
                                        model: gpt-4o
                                      database:
                                        path: /tmp/test.db
                                      agent:
                                        temperature: 0.0
                                        maxTokens: 4096
                                        streaming: true
                                      """,
                              StandardCharsets.UTF_8);

            final var m = TextToSqlCLI.class.getDeclaredMethod("loadConfig", String.class);
            m.setAccessible(true);
            final var config = m.invoke(null, configFile.toAbsolutePath().toString());
            assertNotNull(config);
        }
    }

    // =========================================================================
    // buildAgentSetup (via reflection)
    // =========================================================================

    @Nested
    class RegisterAskUserToolTests {

        @Test
        void registersWithoutThrowing() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            final var agent = buildAgent(config, mapper);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "registerAskUserTool",
                                                               TextToSqlAgent.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, agent));
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var skillsExtension = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgent = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgent",
                                                                        AgentSetup.class,
                                                                        AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            return (TextToSqlAgent) buildAgent.invoke(null, agentSetup, skillsExtension);
        }
    }

    // =========================================================================
    // waitForMcpSseServer (via reflection) — timeout branch
    // =========================================================================

    @Nested
    class RegisterHttpToolboxTests {

        @Test
        void registersWithoutThrowing() throws Exception {
            final var config = buildConfig();
            final var mapper = JsonUtils.createMapper();
            final var agent = buildAgent(config, mapper);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "registerHttpToolbox",
                                                               TextToSqlAgent.class,
                                                               String.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, agent, "http://localhost:19999", mapper));
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var ext = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgentM = TextToSqlCLI.class.getDeclaredMethod(
                                                                         "buildAgent",
                                                                         AgentSetup.class,
                                                                         AgentSkillsExtension.class);
            buildAgentM.setAccessible(true);
            return (TextToSqlAgent) buildAgentM.invoke(null, agentSetup, ext);
        }

        private CliConfig buildConfig() {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            return config;
        }
    }

    // =========================================================================
    // buildOpenAIModel (via reflection)
    // =========================================================================

    @Nested
    class RegisterLocalToolsTests {

        @TempDir
        Path tempDir;

        @Test
        void registersWithoutThrowing() throws Exception {
            final var dbPath = tempDir.resolve("local-tools-test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final var mapper = JsonUtils.createMapper();

            final var agent = buildAgent(config, mapper);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "registerLocalTools",
                                                               TextToSqlAgent.class,
                                                               Path.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, agent, dbPath));
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var skillsExtension = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgent = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgent",
                                                                        AgentSetup.class,
                                                                        AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            return (TextToSqlAgent) buildAgent.invoke(null, agentSetup, skillsExtension);
        }
    }

    // =========================================================================
    // buildAgent (via reflection)
    // =========================================================================

    @Nested
    class ResolveDumpMessagesFilenameTests {

        @Test
        void returnsExplicitFilename() throws Exception {
            final var cli = new TextToSqlCLI();
            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveDumpMessagesFilename", String.class);
            m.setAccessible(true);
            final var result = (String) m.invoke(cli, "/dumpMessages myfile.json");
            assertEquals("myfile.json", result);
        }

        @Test
        void returnsTimestampedFilenameWhenNoArg() throws Exception {
            final var cli = new TextToSqlCLI();
            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveDumpMessagesFilename", String.class);
            m.setAccessible(true);
            final var result = (String) m.invoke(cli, "/dumpMessages");
            assertNotNull(result);
            assertTrue(result.startsWith("messages-"), "Default filename should start with 'messages-'");
            assertTrue(result.endsWith(".json"), "Default filename should end with '.json'");
        }
    }

    // =========================================================================
    // registerAskUserTool (via reflection)
    // =========================================================================

    @Nested
    class ResolveSessionIdTests {

        @Test
        void returnsProvidedSessionId() throws Exception {
            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final var result = (String) m.invoke(null, "my-session-123");
            assertEquals("my-session-123", result);
        }

        @Test
        void returnsUuidForBlankSessionId() throws Exception {
            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final var result = (String) m.invoke(null, "   ");
            assertNotNull(result);
            assertEquals(36, result.length());
        }

        @Test
        void returnsUuidForNullSessionId() throws Exception {
            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final var result = (String) m.invoke(null, (Object) null);
            assertNotNull(result);
            assertFalse(result.isBlank());
            // Should be a valid UUID-ish string (36 chars with dashes)
            assertEquals(36, result.length());
        }
    }

    // =========================================================================
    // registerLocalTools (via reflection)
    // =========================================================================

    @Nested
    class ResolveSkillsDirTests {

        @Test
        void createsTempDirWhenSkillsDirIsNull() throws Exception {
            final var cli = new TextToSqlCLI();
            // skillsDir field is null by default

            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveSkillsDir");
            m.setAccessible(true);
            final var result = (String) m.invoke(cli);
            assertNotNull(result);
            assertFalse(result.isBlank());
            assertTrue(
                       java.nio.file.Files.isDirectory(java.nio.file.Paths.get(result)),
                       "Should return a path to an existing temp directory");
        }

        @Test
        void returnsProvidedSkillsDir() throws Exception {
            final var cli = new TextToSqlCLI();
            final var skillsDirField = TextToSqlCLI.class.getDeclaredField("skillsDir");
            skillsDirField.setAccessible(true);
            skillsDirField.set(cli, "/custom/skills");

            final var m = TextToSqlCLI.class.getDeclaredMethod("resolveSkillsDir");
            m.setAccessible(true);
            final var result = (String) m.invoke(cli);
            assertEquals("/custom/skills", result);
        }
    }

    // =========================================================================
    // validateConfig (via reflection) — non-exit branch
    // =========================================================================

    @Nested
    class RunInteractiveLoopTests {

        private static final class SystemInOverride implements AutoCloseable {
            private final java.io.InputStream original;

            private SystemInOverride(String input) {
                this.original = System.in;
                System.setIn(
                             new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public void close() {
                System.setIn(original);
            }
        }

        private static Stream<Arguments> runInteractiveLoopInputs() {
            return Stream.of(
                             Arguments.of("exit\n"),
                             Arguments.of("quit\n"),
                             Arguments.of("\nexit\n"),
                             Arguments.of(""),
                             Arguments.of("/dumpMessages\nexit\n"));
        }

        @ParameterizedTest
        @MethodSource("runInteractiveLoopInputs")
        void returnsZeroForExitAndControlInputs(String input) throws Exception {
            final var config = buildConfig();
            final var mapper = JsonUtils.createMapper();
            final var agent = buildAgent(config, mapper);

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "runInteractiveLoop",
                                                               TextToSqlAgent.class,
                                                               CliConfig.class,
                                                               String.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);

            try (var ignored = new SystemInOverride(input)) {
                final var result = (int) m.invoke(
                                                  new TextToSqlCLI(),
                                                  agent,
                                                  config,
                                                  "test-session",
                                                  mapper);
                assertEquals(0, result);
            }
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final var buildClient = TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final var adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final var buildModel = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildOpenAIModel",
                                                                        CliConfig.class,
                                                                        OkHttpClientAdapter.class,
                                                                        ObjectMapper.class);
            buildModel.setAccessible(true);
            final var model = (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final var buildSetup = TextToSqlCLI.class.getDeclaredMethod(
                                                                        "buildAgentSetup",
                                                                        CliConfig.class,
                                                                        SimpleOpenAIModel.class,
                                                                        ObjectMapper.class);
            buildSetup.setAccessible(true);
            final var agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final var cli = new TextToSqlCLI();
            final var buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked") final var ext = (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills
                    .invoke(cli);

            final var buildAgentM = TextToSqlCLI.class.getDeclaredMethod(
                                                                         "buildAgent",
                                                                         AgentSetup.class,
                                                                         AgentSkillsExtension.class);
            buildAgentM.setAccessible(true);
            return (TextToSqlAgent) buildAgentM.invoke(null, agentSetup, ext);
        }

        private CliConfig buildConfig() {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            return config;
        }
    }

    // =========================================================================
    // dumpMessages (via reflection) — null-output branch and non-null branch
    // =========================================================================

    @Nested
    class RunInteractiveLoopWithQueryTests {

        private static final class SystemInOverride implements AutoCloseable {
            private final java.io.InputStream original;

            private SystemInOverride(String input) {
                this.original = System.in;
                System.setIn(new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public void close() {
                System.setIn(original);
            }
        }

        @Test
        void realQueryThenExit() throws Exception {
            final var agent = mock(TextToSqlAgent.class);
            final var result = new SqlQueryResult("SELECT 1", List.of(), "ok", 1L);
            final var output = new AgentOutput<>(result, List.of(), List.of(), null, null);
            when(agent.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(output));

            final var config = buildConfig();
            final var mapper = JsonUtils.createMapper();

            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "runInteractiveLoop",
                                                               TextToSqlAgent.class,
                                                               CliConfig.class,
                                                               String.class,
                                                               ObjectMapper.class);
            m.setAccessible(true);

            try (var ignored = new SystemInOverride("show tables\nexit\n")) {
                final var exitCode = (int) m.invoke(new TextToSqlCLI(), agent, config, "test-session", mapper);
                assertEquals(0, exitCode);
            }
        }

        private CliConfig buildConfig() {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            return config;
        }
    }

    // =========================================================================
    // Lambda coverage — outputGenerationTool and outputValidator
    // =========================================================================

    @Nested
    class ValidateConfigTests {

        @Test
        void doesNotThrowWhenApiKeyIsSet() throws Exception {
            final var config = new CliConfig();
            config.getOpenai().setApiKey("a-real-key");

            final var m = TextToSqlCLI.class.getDeclaredMethod("validateConfig", CliConfig.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, config));
        }
    }

    @Nested
    class WaitForMcpSseServerTests {

        @Test
        void returnsWhenPortReachable() throws Exception {
            try (var ss = new java.net.ServerSocket(0)) {
                final var port = ss.getLocalPort();
                final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                                   "waitForMcpSseServer",
                                                                   String.class,
                                                                   int.class,
                                                                   long.class);
                m.setAccessible(true);
                assertDoesNotThrow(
                                   () -> m.invoke(null, "localhost", port, 5_000L),
                                   "waitForMcpSseServer should succeed when port is listening");
            }
        }

        @Test
        void throwsWhenPortNotReachable() throws Exception {
            // Use port 1 — guaranteed to be unused/unreachable in test environments
            final var m = TextToSqlCLI.class.getDeclaredMethod(
                                                               "waitForMcpSseServer",
                                                               String.class,
                                                               int.class,
                                                               long.class);
            m.setAccessible(true);

            final var ex = assertThrows(
                                        InvocationTargetException.class,
                                        () -> m.invoke(null, "localhost", 1, 300L));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("did not start"));
        }
    }
}
