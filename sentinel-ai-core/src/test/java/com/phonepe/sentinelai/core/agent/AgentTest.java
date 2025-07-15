package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for {@link Agent}
 */
@Slf4j
class AgentTest {

    private record Input(String data) {
    }

    private record Output(String output) {
    }

    private static final class TestAgent extends Agent<String, String, TestAgent> {

        public TestAgent(
                @NonNull AgentSetup setup,
                List<AgentExtension<String, String, TestAgent>> extensions,
                Map<String, ExecutableTool> knownTools) {
            this(setup, extensions, knownTools, new ApproveAllToolRuns<>());
        }

        public TestAgent(
                @NonNull AgentSetup setup,
                List<AgentExtension<String, String, TestAgent>> extensions,
                Map<String, ExecutableTool> knownTools,
                ToolRunApprovalSeeker<String, String, TestAgent> toolRunApprovalSeeker) {
            super(String.class, "This is irrelevant", setup, extensions, knownTools, toolRunApprovalSeeker);
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Tool("Return name of user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Tool that throws exception")
        public void throwTool() {
            throw new RuntimeException("Test exception");
        }

        @Tool("Returns sessions summary")
        public String sessionSummary(AgentRunContext<String> context, String input) {
            if (context.getRequestMetadata().getSessionId().equals("s1")) {
                return "Session summary: " + input;
            }
            throw new IllegalArgumentException("Invalid session id");
        }

        @Tool("Tool that doesn't return anything")
        public void voidTool() {
            // Do nothing
        }

        @Tool("Tool that takes structured input and returns structured output")
        public Output structuredTool(final Input input) {
            return new Output("Hello " + input.data);
        }
    }

    @Test
    void testToolCall() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {

                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey("test_agent_get_name"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_get_name",
                                            "{}"));
                            assertTrue(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.success(objectMapper.createObjectNode()
                                            .textNode("Hello " + response.getResponse()),
                                    List.of(message),
                                    messages,
                                    context.getModelUsageStats());
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertTrue(response.getData().contains("Santanu"));
    }

    @Test
    void testToolCallNoApproval() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {
                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey("test_agent_get_name"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_get_name",
                                            "{}"));
                            assertFalse(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.success(objectMapper.createObjectNode()
                                            .textNode("Tool call not approved"),
                                    List.of(message),
                                    messages,
                                    context.getModelUsageStats());
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of(),
                (agent, runContext, toolCall) -> {
                    return !"test_agent_get_name".equals(toolCall.getToolName());
                }
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertTrue(response.getData().equals("Tool call not approved"));
    }

    @Test
    void testContextAwareToolCall() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {
                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey(
                                    "test_agent_session_summary"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_session_summary",
                                            """
                                                    { "input": "Test Data" }
                                                    """));
                            assertTrue(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.success(objectMapper.createObjectNode()
                                            .textNode("Hello " + response.getResponse()),
                                    List.of(message),
                                    messages,
                                    context.getModelUsageStats());
                        });
                    }

                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertTrue(response.getData().contains("Session summary: Test Data"));
    }

    @Test
    void testVoidToolCall() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {

                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey("test_agent_void_tool"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_void_tool",
                                            "{}"));
                            assertTrue(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.success(objectMapper.createObjectNode()
                                            .textNode("Hello " + response.getResponse()),
                                    List.of(message),
                                    messages,
                                    context.getModelUsageStats());
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertTrue(response.getData().contains("Hello success"));
    }

    @Test
    void testStructuredToolCall() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {
                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey(
                                    "test_agent_structured_tool"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_structured_tool",
                                            """
                                                    {
                                                       "input": {
                                                           "data" : "Test Data"
                                                       }
                                                    }
                                                    """));
                            assertTrue(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.success(objectMapper.createObjectNode()
                                            .textNode("Hello " + response.getResponse()),
                                    List.of(message),
                                    messages,
                                    context.getModelUsageStats());
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        log.debug("Tool response: {}", response.getData());
        assertTrue(response.getData().contains("Hello Test Data"));
    }

    @Test
    void testToolCallFailure() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {

                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            assertTrue(tools.containsKey("test_agent_throw_tool"));
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "test_agent_throw_tool",
                                            "{}"));
                            assertFalse(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.error(messages,
                                    context.getModelUsageStats(),
                                    SentinelError.error(
                                            ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                            response.getResponse()));
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertNull(response.getData());
        final var data = response.getError();
        assertTrue(data.getMessage().contains("Test exception"));
    }

    @Test
    void testToolCallWrongName() {
        final var objectMapper = JsonUtils.createMapper();
        final var textAgent = new TestAgent(AgentSetup.builder()
                .model(new Model() {
                    @Override
                    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
                            AgentRunContext<R> context,
                            JsonNode responseSchema,
                            Map<String, ExecutableTool> tools,
                            ToolRunner<R> toolRunner,
                            List<AgentExtension<R, T, A>> extensions,
                            A agent) {
                        return CompletableFuture.supplyAsync(() -> {
                            final var response = toolRunner.runTool(
                                    context,
                                    tools,
                                    new ToolCall("TC1",
                                            "getUnknown",
                                            "{}"));
                            assertFalse(response.isSuccess());
                            assertEquals("TC1", response.getToolCallId());
                            final var messages =
                                    new ArrayList<>(context.getOldMessages());
                            final var message =
                                    new ToolCallResponse(response.getToolCallId(),
                                            response.getToolName(),
                                            response.getErrorType(),
                                            response.getResponse(),
                                            LocalDateTime.now());
                            messages.add(message);
                            return ModelOutput.error(messages,
                                    context.getModelUsageStats(),
                                    SentinelError.error(
                                            ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                            response.getResponse()));
                        });
                    }
                })
                .modelSettings(ModelSettings.builder()
                        .build())
                .mapper(objectMapper)
                .build(),
                List.of(),
                Map.of()
        );
        final var response = textAgent.execute(
                AgentInput.<String>builder()
                        .request("Hi")
                        .requestMetadata(
                                AgentRequestMetadata.builder()
                                        .sessionId("s1")
                                        .userId("ss").build())
                        .build());
        assertNull(response.getData());
        final var data = response.getError();
        assertTrue(data.getMessage().contains("Invalid tool: getUnknown"));
    }
}