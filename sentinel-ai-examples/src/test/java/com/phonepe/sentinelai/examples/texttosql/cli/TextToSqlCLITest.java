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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the constants and enum types exposed by {@link TextToSqlCLI}.
 *
 * <p>The {@link TextToSqlCLI#call()} method requires a live OpenAI API key, an initialised SQLite
 * database, and several external services — these are covered by end-to-end / integration tests.
 * Here we focus on the public API surface that can be exercised without infrastructure.
 */
@DisplayName("TextToSqlCLI")
class TextToSqlCLITest {

    // =========================================================================
    // ToolboxMode enum
    // =========================================================================

    @Test
    @DisplayName("ToolboxMode enum has HTTP and MCP values")
    void toolboxModeEnumValues() {
        final TextToSqlCLI.ToolboxMode[] values = TextToSqlCLI.ToolboxMode.values();
        assertEquals(2, values.length);
        assertEquals(TextToSqlCLI.ToolboxMode.HTTP, values[0]);
        assertEquals(TextToSqlCLI.ToolboxMode.MCP, values[1]);
    }

    @Test
    @DisplayName("ToolboxMode valueOf works for HTTP and MCP")
    void toolboxModeValueOf() {
        assertEquals(TextToSqlCLI.ToolboxMode.HTTP, TextToSqlCLI.ToolboxMode.valueOf("HTTP"));
        assertEquals(TextToSqlCLI.ToolboxMode.MCP, TextToSqlCLI.ToolboxMode.valueOf("MCP"));
    }

    // =========================================================================
    // McpServerMode enum
    // =========================================================================

    @Test
    @DisplayName("McpServerMode enum has STDIO and SSE values")
    void mcpServerModeEnumValues() {
        final TextToSqlCLI.McpServerMode[] values = TextToSqlCLI.McpServerMode.values();
        assertEquals(2, values.length);
        assertEquals(TextToSqlCLI.McpServerMode.STDIO, values[0]);
        assertEquals(TextToSqlCLI.McpServerMode.SSE, values[1]);
    }

    @Test
    @DisplayName("McpServerMode valueOf works for STDIO and SSE")
    void mcpServerModeValueOf() {
        assertEquals(
                TextToSqlCLI.McpServerMode.STDIO, TextToSqlCLI.McpServerMode.valueOf("STDIO"));
        assertEquals(TextToSqlCLI.McpServerMode.SSE, TextToSqlCLI.McpServerMode.valueOf("SSE"));
    }

    // =========================================================================
    // DEFAULT_MCP_SSE_PORT constant
    // =========================================================================

    @Test
    @DisplayName("DEFAULT_MCP_SSE_PORT equals SqliteMcpServer.DEFAULT_SSE_PORT")
    void defaultMcpSsePortMatchesSqliteMcpServerDefault() {
        assertEquals(SqliteMcpServer.DEFAULT_SSE_PORT, TextToSqlCLI.DEFAULT_MCP_SSE_PORT);
    }

    // =========================================================================
    // Constructor / instantiation
    // =========================================================================

    @Test
    @DisplayName("TextToSqlCLI can be instantiated without throwing")
    void canBeInstantiatedWithoutThrowing() {
        assertDoesNotThrow(TextToSqlCLI::new);
    }

    // =========================================================================
    // resolveSessionId (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("resolveSessionId")
    class ResolveSessionIdTests {

        @Test
        @DisplayName("returns a UUID when sessionId is null")
        void returnsUuidForNullSessionId() throws Exception {
            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final String result = (String) m.invoke(null, (Object) null);
            assertNotNull(result);
            assertFalse(result.isBlank());
            // Should be a valid UUID-ish string (36 chars with dashes)
            assertEquals(36, result.length());
        }

        @Test
        @DisplayName("returns a UUID when sessionId is blank")
        void returnsUuidForBlankSessionId() throws Exception {
            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final String result = (String) m.invoke(null, "   ");
            assertNotNull(result);
            assertEquals(36, result.length());
        }

        @Test
        @DisplayName("returns the provided sessionId when it is non-blank")
        void returnsProvidedSessionId() throws Exception {
            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("resolveSessionId", String.class);
            m.setAccessible(true);
            final String result = (String) m.invoke(null, "my-session-123");
            assertEquals("my-session-123", result);
        }
    }

    // =========================================================================
    // resolveSkillsDir (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("resolveSkillsDir")
    class ResolveSkillsDirTests {

        @Test
        @DisplayName("returns provided skillsDir when it is set")
        void returnsProvidedSkillsDir() throws Exception {
            final TextToSqlCLI cli = new TextToSqlCLI();
            final Field skillsDirField = TextToSqlCLI.class.getDeclaredField("skillsDir");
            skillsDirField.setAccessible(true);
            skillsDirField.set(cli, "/custom/skills");

            final Method m = TextToSqlCLI.class.getDeclaredMethod("resolveSkillsDir");
            m.setAccessible(true);
            final String result = (String) m.invoke(cli);
            assertEquals("/custom/skills", result);
        }

        @Test
        @DisplayName("creates a temp directory with extracted skill when skillsDir is null")
        void createsTempDirWhenSkillsDirIsNull() throws Exception {
            final TextToSqlCLI cli = new TextToSqlCLI();
            // skillsDir field is null by default

            final Method m = TextToSqlCLI.class.getDeclaredMethod("resolveSkillsDir");
            m.setAccessible(true);
            final String result = (String) m.invoke(cli);
            assertNotNull(result);
            assertFalse(result.isBlank());
            assertTrue(
                    java.nio.file.Files.isDirectory(java.nio.file.Paths.get(result)),
                    "Should return a path to an existing temp directory");
        }
    }

    // =========================================================================
    // loadConfig (via reflection) — success and missing-file branches
    // =========================================================================

    @Nested
    @DisplayName("loadConfig")
    class LoadConfigTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("loads a valid YAML config file successfully")
        void loadsValidYamlConfig() throws Exception {
            final Path configFile = tempDir.resolve("agent-config.yml");
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

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("loadConfig", String.class);
            m.setAccessible(true);
            final Object config = m.invoke(null, configFile.toAbsolutePath().toString());
            assertNotNull(config);
        }
    }

    // =========================================================================
    // initializeDatabase (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("initializeDatabase")
    class InitializeDatabaseTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("initialises the SQLite database and returns the absolute path")
        void initialisesDatabase() throws Exception {
            final Path dbFile = tempDir.resolve("test.db");
            final CliConfig config = new CliConfig();
            config.getDatabase().setPath(dbFile.toAbsolutePath().toString());

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("initializeDatabase", CliConfig.class);
            m.setAccessible(true);
            final Path result = (Path) m.invoke(null, config);

            assertNotNull(result);
            assertTrue(result.isAbsolute(), "Returned path must be absolute");
            assertTrue(Files.exists(result), "Database file must exist after initialisation");
        }
    }

    // =========================================================================
    // buildTrustedHttpClient (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("buildTrustedHttpClient")
    class BuildTrustedHttpClientTests {

        @Test
        @DisplayName("returns a non-null OkHttpClientAdapter")
        void returnsNonNullAdapter() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setBearerPrefix("Bearer ");

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildTrustedHttpClient", CliConfig.class);
            m.setAccessible(true);
            final Object result = m.invoke(null, config);

            assertNotNull(result);
            assertInstanceOf(OkHttpClientAdapter.class, result);
        }
    }

    // =========================================================================
    // buildAgentSetup (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("buildAgentSetup")
    class BuildAgentSetupTests {

        @Test
        @DisplayName("returns a non-null AgentSetup")
        void returnsNonNullAgentSetup() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);

            final ObjectMapper mapper = JsonUtils.createMapper();
            final OkHttpClientAdapter clientAdapter =
                    (OkHttpClientAdapter) invokeStaticMethod(
                            "buildTrustedHttpClient", new Class<?>[]{CliConfig.class}, config);
            final Object model =
                    invokeStaticMethod(
                            "buildOpenAIModel",
                            new Class<?>[]{CliConfig.class, OkHttpClientAdapter.class, ObjectMapper.class},
                            config, clientAdapter, mapper);

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup",
                            CliConfig.class,
                            com.phonepe.sentinelai.models.SimpleOpenAIModel.class,
                            ObjectMapper.class);
            m.setAccessible(true);
            final Object agentSetup = m.invoke(null, config, model, mapper);

            assertNotNull(agentSetup);
        }

        private Object invokeStaticMethod(String name, Class<?>[] paramTypes, Object... args)
                throws Exception {
            final Method m = TextToSqlCLI.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        }
    }

    // =========================================================================
    // waitForMcpSseServer (via reflection) — timeout branch
    // =========================================================================

    @Nested
    @DisplayName("waitForMcpSseServer")
    class WaitForMcpSseServerTests {

        @Test
        @DisplayName("throws IllegalStateException when port is not reachable within timeout")
        void throwsWhenPortNotReachable() throws Exception {
            // Use port 1 — guaranteed to be unused/unreachable in test environments
            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "waitForMcpSseServer", String.class, int.class, long.class);
            m.setAccessible(true);

            final InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(null, "localhost", 1, 300L));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("did not start"));
        }
    }

    // =========================================================================
    // buildOpenAIModel (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("buildOpenAIModel")
    class BuildOpenAIModelTests {

        @Test
        @DisplayName("returns a non-null SimpleOpenAIModel")
        void returnsNonNullModel() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");

            final ObjectMapper mapper = JsonUtils.createMapper();
            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter clientAdapter =
                    (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel",
                            CliConfig.class,
                            OkHttpClientAdapter.class,
                            ObjectMapper.class);
            m.setAccessible(true);
            final Object model = m.invoke(null, config, clientAdapter, mapper);

            assertNotNull(model);
            assertInstanceOf(SimpleOpenAIModel.class, model);
        }
    }

    // =========================================================================
    // buildAgent (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("buildAgent")
    class BuildAgentTests {

        @Test
        @DisplayName("builds a TextToSqlAgent successfully")
        void buildsAgentSuccessfully() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final ObjectMapper mapper = JsonUtils.createMapper();

            // build prerequisites
            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter adapter = (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method buildModel =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel", CliConfig.class, OkHttpClientAdapter.class, ObjectMapper.class);
            buildModel.setAccessible(true);
            final SimpleOpenAIModel<?> model =
                    (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final Method buildSetup =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup", CliConfig.class, SimpleOpenAIModel.class, ObjectMapper.class);
            buildSetup.setAccessible(true);
            final AgentSetup agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            // build skills extension via instance method
            final TextToSqlCLI cli = new TextToSqlCLI();
            final Method buildSkills =
                    TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked")
            final AgentSkillsExtension<String, ?, TextToSqlAgent> skillsExtension =
                    (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills.invoke(cli);

            final Method buildAgent =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgent", AgentSetup.class, AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            final Object agent = buildAgent.invoke(null, agentSetup, skillsExtension);

            assertNotNull(agent);
            assertInstanceOf(TextToSqlAgent.class, agent);
        }
    }

    // =========================================================================
    // registerAskUserTool (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("registerAskUserTool")
    class RegisterAskUserToolTests {

        @Test
        @DisplayName("registers ask-user tool without throwing")
        void registersWithoutThrowing() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final ObjectMapper mapper = JsonUtils.createMapper();

            final TextToSqlAgent agent = buildAgent(config, mapper);

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "registerAskUserTool", TextToSqlAgent.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, agent));
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter adapter =
                    (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method buildModel =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel", CliConfig.class, OkHttpClientAdapter.class, ObjectMapper.class);
            buildModel.setAccessible(true);
            final SimpleOpenAIModel<?> model =
                    (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final Method buildSetup =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup", CliConfig.class, SimpleOpenAIModel.class, ObjectMapper.class);
            buildSetup.setAccessible(true);
            final AgentSetup agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final TextToSqlCLI cli = new TextToSqlCLI();
            final Method buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked")
            final AgentSkillsExtension<String, ?, TextToSqlAgent> skillsExtension =
                    (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills.invoke(cli);

            final Method buildAgent =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgent", AgentSetup.class, AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            return (TextToSqlAgent) buildAgent.invoke(null, agentSetup, skillsExtension);
        }
    }

    // =========================================================================
    // registerLocalTools (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("registerLocalTools")
    class RegisterLocalToolsTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("registers local tools without throwing")
        void registersWithoutThrowing() throws Exception {
            final Path dbPath = tempDir.resolve("local-tools-test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-api-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final ObjectMapper mapper = JsonUtils.createMapper();

            final TextToSqlAgent agent = buildAgent(config, mapper);

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "registerLocalTools", TextToSqlAgent.class, Path.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, agent, dbPath));
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter adapter =
                    (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method buildModel =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel", CliConfig.class, OkHttpClientAdapter.class, ObjectMapper.class);
            buildModel.setAccessible(true);
            final SimpleOpenAIModel<?> model =
                    (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final Method buildSetup =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup", CliConfig.class, SimpleOpenAIModel.class, ObjectMapper.class);
            buildSetup.setAccessible(true);
            final AgentSetup agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final TextToSqlCLI cli = new TextToSqlCLI();
            final Method buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked")
            final AgentSkillsExtension<String, ?, TextToSqlAgent> skillsExtension =
                    (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills.invoke(cli);

            final Method buildAgent =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgent", AgentSetup.class, AgentSkillsExtension.class);
            buildAgent.setAccessible(true);
            return (TextToSqlAgent) buildAgent.invoke(null, agentSetup, skillsExtension);
        }
    }

    // =========================================================================
    // validateConfig (via reflection) — non-exit branch
    // =========================================================================

    @Nested
    @DisplayName("validateConfig")
    class ValidateConfigTests {

        @Test
        @DisplayName("does not throw when apiKey is set")
        void doesNotThrowWhenApiKeyIsSet() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("a-real-key");

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod("validateConfig", CliConfig.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(null, config));
        }
    }

    // =========================================================================
    // dumpMessages (via reflection) — null-output branch and non-null branch
    // =========================================================================

    @Nested
    @DisplayName("dumpMessages")
    class DumpMessagesTests {

        @Test
        @DisplayName("prints warning when lastAgentOutput is null")
        void printsWarningWhenNoOutput() throws Exception {
            final TextToSqlCLI cli = new TextToSqlCLI();
            // lastAgentOutput is null by default — exercising the early-return branch
            final ObjectMapper mapper = JsonUtils.createMapper();

            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "dumpMessages", String.class, ObjectMapper.class);
            m.setAccessible(true);
            // Should not throw; just prints a warning
            assertDoesNotThrow(() -> m.invoke(cli, "messages-test.json", mapper));
        }

        @Test
        @DisplayName("writes messages to .logs/ when lastAgentOutput is set")
        void writesMessagesToLogsDirectory() throws Exception {
            // Ensure .logs/ directory exists (it's created by the production code)
            final Path logsDir = Path.of(".logs");
            Files.createDirectories(logsDir);

            final TextToSqlCLI cli = new TextToSqlCLI();
            final ObjectMapper mapper = JsonUtils.createMapper();

            // Inject a non-null AgentOutput via reflection
            @SuppressWarnings("unchecked")
            final AgentOutput<Object> output = new AgentOutput<>(null, List.of(), List.of(), null, null);
            final Field lastOutputField =
                    TextToSqlCLI.class.getDeclaredField("lastAgentOutput");
            lastOutputField.setAccessible(true);
            lastOutputField.set(cli, output);

            final String filename = "test-dump-" + System.nanoTime() + ".json";
            final Method m =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "dumpMessages", String.class, ObjectMapper.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(cli, filename, mapper));

            // Verify the file was written
            assertTrue(Files.exists(logsDir.resolve(filename)),
                    "Output file should exist in .logs/");
        }
    }

    // =========================================================================
    // Lambda coverage — outputGenerationTool and outputValidator
    // =========================================================================

    @Nested
    @DisplayName("Lambda coverage")
    class LambdaCoverageTests {

        @Test
        @DisplayName("outputGenerationTool lambda returns the input unchanged")
        void outputGenerationToolLambdaReturnsInput() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final ObjectMapper mapper = JsonUtils.createMapper();

            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter adapter =
                    (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method buildModel =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel",
                            CliConfig.class,
                            OkHttpClientAdapter.class,
                            ObjectMapper.class);
            buildModel.setAccessible(true);
            final SimpleOpenAIModel<?> model =
                    (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final Method buildSetup =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup", CliConfig.class, SimpleOpenAIModel.class, ObjectMapper.class);
            buildSetup.setAccessible(true);
            final AgentSetup agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            // Directly invoke the outputGenerationTool lambda
            final Field toolField = AgentSetup.class.getDeclaredField("outputGenerationTool");
            toolField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final UnaryOperator<String> tool = (UnaryOperator<String>) toolField.get(agentSetup);
            assertNotNull(tool);
            assertEquals("hello", tool.apply("hello"));
        }

        @Test
        @DisplayName("outputValidator lambda returns success")
        @SuppressWarnings("unchecked")
        void outputValidatorLambdaReturnsSuccess() throws Exception {
            final CliConfig config = new CliConfig();
            config.getOpenai().setApiKey("test-key");
            config.getOpenai().setModel("gpt-4o");
            config.getOpenai().setBaseUrl("https://api.openai.com/v1");
            config.getAgent().setTemperature(0.0f);
            config.getAgent().setMaxTokens(4096);
            config.getAgent().setStreaming(false);
            final ObjectMapper mapper = JsonUtils.createMapper();

            final TextToSqlAgent agent = buildAgent(config, mapper);

            // Get the outputValidator field from Agent superclass
            final Field validatorField = agent.getClass().getSuperclass().getDeclaredField("outputValidator");
            validatorField.setAccessible(true);
            final OutputValidator<String, ?> validator =
                    (OutputValidator<String, ?>) validatorField.get(agent);
            assertNotNull(validator);

            // Invoke with null context and null output — the lambda ignores both args
            final OutputValidationResults result = validator.validate(null, null);
            assertNotNull(result);
            assertTrue(result.isSuccessful(), "outputValidator lambda should always return success");
        }

        private TextToSqlAgent buildAgent(CliConfig config, ObjectMapper mapper) throws Exception {
            final Method buildClient =
                    TextToSqlCLI.class.getDeclaredMethod("buildTrustedHttpClient", CliConfig.class);
            buildClient.setAccessible(true);
            final OkHttpClientAdapter adapter =
                    (OkHttpClientAdapter) buildClient.invoke(null, config);

            final Method buildModel =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildOpenAIModel",
                            CliConfig.class,
                            OkHttpClientAdapter.class,
                            ObjectMapper.class);
            buildModel.setAccessible(true);
            final SimpleOpenAIModel<?> model =
                    (SimpleOpenAIModel<?>) buildModel.invoke(null, config, adapter, mapper);

            final Method buildSetup =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgentSetup", CliConfig.class, SimpleOpenAIModel.class, ObjectMapper.class);
            buildSetup.setAccessible(true);
            final AgentSetup agentSetup = (AgentSetup) buildSetup.invoke(null, config, model, mapper);

            final TextToSqlCLI cli = new TextToSqlCLI();
            final Method buildSkills = TextToSqlCLI.class.getDeclaredMethod("buildSkillsExtension");
            buildSkills.setAccessible(true);
            @SuppressWarnings("unchecked")
            final AgentSkillsExtension<String, ?, TextToSqlAgent> skillsExtension =
                    (AgentSkillsExtension<String, ?, TextToSqlAgent>) buildSkills.invoke(cli);

            final Method buildAgentMethod =
                    TextToSqlCLI.class.getDeclaredMethod(
                            "buildAgent", AgentSetup.class, AgentSkillsExtension.class);
            buildAgentMethod.setAccessible(true);
            return (TextToSqlAgent) buildAgentMethod.invoke(null, agentSetup, skillsExtension);
        }
    }
}
