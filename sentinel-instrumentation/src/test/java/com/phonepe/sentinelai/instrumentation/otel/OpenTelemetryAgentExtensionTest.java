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

package com.phonepe.sentinelai.instrumentation.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ApproveAllToolRuns;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.outputvalidation.DefaultOutputValidator;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenTelemetryAgentExtensionTest {

    private static final AttributeKey<String> ATTR_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> ATTR_PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> ATTR_AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> ATTR_CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    private static final AttributeKey<String> ATTR_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> ATTR_TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> ATTR_TOOL_CALL_ARGUMENTS = AttributeKey.stringKey(
                                                                                                "gen_ai.tool.call.arguments");
    private static final AttributeKey<String> ATTR_TOOL_CALL_RESULT = AttributeKey.stringKey("gen_ai.tool.call.result");
    private static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");

    private static final class DummyAgent extends Agent<String, String, DummyAgent> {
        DummyAgent(AgentSetup setup,
                   List<AgentExtension<String, String, DummyAgent>> extensions,
                   Map<String, ExecutableTool> tools,
                   ToolRunApprovalSeeker<String, String, DummyAgent> approvalSeeker) {
            super(String.class,
                  "dummy",
                  setup,
                  extensions,
                  tools,
                  approvalSeeker,
                  new DefaultOutputValidator<>(),
                  new DefaultErrorHandler<>(),
                  new NeverTerminateEarlyStrategy());
        }

        @Override
        public String name() {
            return "dummy";
        }
    }

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;

    private OpenTelemetrySdk openTelemetrySdk;

    @BeforeEach
    void setUp() {
        this.spanExporter = InMemorySpanExporter.create();
        this.tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        this.openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Test
    void shouldCloseDanglingToolSpanWhenRunCompletesWithoutTerminalToolEvent() {
        final var model = mock(Model.class);
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(orphanToolFlow());
        final var agent = createAgent(model,
                                      Map.of(),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .build(),
                                      new ApproveAllToolRuns<>());

        agent.execute(agentInput("run-6", "session-6"));

        final var toolSpan = waitForSpanByName("execute_tool dangling-tool");
        assertNotNull(toolSpan);
        assertEquals("tool_call_incomplete", toolSpan.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldCloseRunSpanViaOnRequestCompletedWhenOutputEventsAreMissing() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                        .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                        .build())
                .build();
        final var agent = createAgent(mock(Model.class),
                                      Map.of(),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .build(),
                                      new ApproveAllToolRuns<>());

        extension.onExtensionRegistrationCompleted(agent);
        extension.consumeEvent(new InputReceivedAgentEvent("dummy",
                                                           "run-request-completed",
                                                           "session",
                                                           "user",
                                                           "hello"));
        assertEquals(0, finishedSpans().size());

        final var context = new AgentRunContext<>("run-request-completed",
                                                  "hello",
                                                  AgentRequestMetadata.builder()
                                                          .runId("run-request-completed")
                                                          .sessionId("session")
                                                          .userId("user")
                                                          .build(),
                                                  agent.getSetup(),
                                                  new java.util.ArrayList<>(),
                                                  new ModelUsageStats(),
                                                  ProcessingMode.DIRECT);
        agent.onRequestCompleted()
                .dispatch(new Agent.ProcessingCompletedData<>(agent,
                                                              agent.getSetup(),
                                                              context,
                                                              null,
                                                              null,
                                                              ProcessingMode.DIRECT));

        final var runSpan = waitForSpanByName("invoke_agent dummy");
        assertNotNull(runSpan);
    }

    @Test
    void shouldCloseStaleRunSpanOnlyWhenAnotherEventArrives() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                        .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                        .maxActiveSpanDuration(Duration.ZERO)
                        .build())
                .build();

        extension.consumeEvent(new InputReceivedAgentEvent("dummy",
                                                           "run-stale",
                                                           "session-stale",
                                                           "user",
                                                           "hello"));
        assertEquals(0, finishedSpans().size());

        extension.consumeEvent(new MessageSentAgentEvent("dummy",
                                                         "run-other",
                                                         "session-other",
                                                         "user",
                                                         List.of(),
                                                         List.of()));

        final var runSpan = spanByName("invoke_agent dummy");
        assertNotNull(runSpan);
        assertEquals("run_incomplete", runSpan.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldEmitExecuteToolSpanWithErrorForFailedToolCall() {
        final var model = mock(Model.class);
        final var tool = externalTool("lookup-order",
                                      new ExternalTool.ExternalToolResponse("tool failed",
                                                                            ErrorType.TOOL_CALL_PERMANENT_FAILURE));
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(
                            runWithTool("lookup-order",
                                        "call-2",
                                        "{\"orderId\":\"o-1\"}"));
        final var agent = createAgent(model,
                                      Map.of("lookup-order", tool),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .captureToolCallArguments(true)
                                              .captureToolCallResult(true)
                                              .build(),
                                      new ApproveAllToolRuns<>());

        agent.execute(agentInput("run-2", "session-2"));

        final var spans = finishedSpans();
        assertEquals(2, spans.size());

        final var span = spanByName("execute_tool lookup-order");
        assertNotNull(span);
        assertEquals("execute_tool lookup-order", span.getName());
        assertEquals("execute_tool", span.getAttributes().get(ATTR_OPERATION_NAME));
        assertEquals("lookup-order", span.getAttributes().get(ATTR_TOOL_NAME));
        assertEquals("call-2", span.getAttributes().get(ATTR_TOOL_CALL_ID));
        assertEquals("{\"orderId\":\"o-1\"}", span.getAttributes().get(ATTR_TOOL_CALL_ARGUMENTS));
        assertEquals("TOOL_CALL_PERMANENT_FAILURE", span.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldEmitExecuteToolSpanWithResultForSuccessfulToolCall() {
        final var model = mock(Model.class);
        final var tool = externalTool("get-weather",
                                      new ExternalTool.ExternalToolResponse(Map.of("temp", 22), ErrorType.SUCCESS));
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(
                            runWithTool("get-weather",
                                        "call-3",
                                        "{\"city\":\"Paris\"}"));
        final var agent = createAgent(model,
                                      Map.of("get-weather", tool),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .captureToolCallArguments(true)
                                              .captureToolCallResult(true)
                                              .build(),
                                      new ApproveAllToolRuns<>());

        agent.execute(agentInput("run-3", "session-3"));

        final var spans = finishedSpans();
        assertEquals(2, spans.size());
        final var toolSpan = spanByName("execute_tool get-weather");
        assertNotNull(toolSpan);
        assertEquals("execute_tool get-weather", toolSpan.getName());
        assertEquals("execute_tool", toolSpan.getAttributes().get(ATTR_OPERATION_NAME));
        assertEquals("get-weather", toolSpan.getAttributes().get(ATTR_TOOL_NAME));
        assertEquals("call-3", toolSpan.getAttributes().get(ATTR_TOOL_CALL_ID));
        assertEquals("{\"city\":\"Paris\"}", toolSpan.getAttributes().get(ATTR_TOOL_CALL_ARGUMENTS));
        assertEquals("{\"temp\":22}", toolSpan.getAttributes().get(ATTR_TOOL_CALL_RESULT));
    }

    @Test
    void shouldEmitInvokeAgentSpanOnInputAndOutputEvents() {
        final var model = mock(Model.class);
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(
                            successfulOutput("hi",
                                             120,
                                             40));
        final var agent = createAgent(model,
                                      Map.of(),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .providerName("openai")
                                              .build(),
                                      new ApproveAllToolRuns<>());

        agent.execute(agentInput("run-1", "session-1"));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());

        final var span = spanByName("invoke_agent dummy");
        assertNotNull(span);
        assertEquals("invoke_agent dummy", span.getName());
        assertEquals("invoke_agent", span.getAttributes().get(ATTR_OPERATION_NAME));
        assertEquals("openai", span.getAttributes().get(ATTR_PROVIDER_NAME));
        assertEquals("dummy", span.getAttributes().get(ATTR_AGENT_NAME));
        assertEquals("session-1", span.getAttributes().get(ATTR_CONVERSATION_ID));
    }

    @Test
    void shouldEvictStaleToolSpanEvenWhenRequestCompletedEventIsMissing() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                        .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                        .maxActiveSpanDuration(Duration.ZERO)
                        .build())
                .build();

        extension.consumeEvent(new ToolCalledAgentEvent("dummy",
                                                        "run-stale",
                                                        "session-stale",
                                                        "user",
                                                        "call-stale",
                                                        "stale-tool",
                                                        "{}"));
        extension.consumeEvent(new MessageSentAgentEvent("dummy",
                                                         "run-other",
                                                         "session-other",
                                                         "user",
                                                         List.of(),
                                                         List.of()));

        final var toolSpan = spanByName("execute_tool stale-tool");
        assertNotNull(toolSpan);
        assertEquals("tool_call_incomplete", toolSpan.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldKeepRunSpanOpenWhenOutputEventsAreMissing() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                        .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                        .build())
                .build();

        extension.consumeEvent(new InputReceivedAgentEvent("dummy", "run-no-output", "session", "user", "hello"));

        assertEquals(0, finishedSpans().size());
    }

    @Test
    void shouldMarkInvokeAgentSpanErrorWhenOutputFails() {
        final var model = mock(Model.class);
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(
                            failedOutput(ErrorType.LENGTH_EXCEEDED));
        final var agent = createAgent(model,
                                      Map.of(),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .build(),
                                      new ApproveAllToolRuns<>());

        agent.execute(agentInput("run-5", "session-5"));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());
        assertEquals("LENGTH_EXCEEDED", spans.get(0).getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldMarkToolSpanErrorWhenApprovalIsDenied() {
        final var model = mock(Model.class);
        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer(
                            denialFlow());
        final var tool = externalTool("transfer-funds", new ExternalTool.ExternalToolResponse("ok", ErrorType.SUCCESS));
        final var agent = createAgent(model,
                                      Map.of("transfer-funds", tool),
                                      OpenTelemetryAgentExtensionSetup.builder()
                                              .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                                              .build(),
                                      (a, c, t) -> false);

        agent.execute(agentInput("run-4", "session-4"));

        final var toolSpan = waitForSpanByName("execute_tool transfer-funds");
        assertNotNull(toolSpan);
        assertEquals("tool_call_approval_denied", toolSpan.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        openTelemetrySdk.close();
    }

    private AgentInput<String> agentInput(String runId, String sessionId) {
        return AgentInput.<String>builder()
                .request("hello")
                .requestMetadata(AgentRequestMetadata.builder()
                        .runId(runId)
                        .sessionId(sessionId)
                        .userId("user")
                        .build())
                .build();
    }

    private DummyAgent createAgent(Model model,
                                   Map<String, ExecutableTool> tools,
                                   OpenTelemetryAgentExtensionSetup otelSetup,
                                   ToolRunApprovalSeeker<String, String, DummyAgent> toolRunApprovalSeeker) {
        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(model)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build();
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(otelSetup)
                .build();
        return new DummyAgent(setup, List.of(extension), tools, toolRunApprovalSeeker);
    }

    private Answer<CompletableFuture<ModelOutput>> denialFlow() {
        return invocation -> {
            final var context = invocation.getArgument(0, com.phonepe.sentinelai.core.model.ModelRunContext.class);
            context.getAgentSetup().getEventBus().notify(new ToolCalledAgentEvent("dummy",
                                                                                  context.getRunId(),
                                                                                  context.getSessionId(),
                                                                                  context.getUserId(),
                                                                                  "call-4",
                                                                                  "transfer-funds",
                                                                                  "{\"amount\":5000}"));
            context.getAgentSetup().getEventBus().notify(new ToolCallApprovalDeniedAgentEvent("dummy",
                                                                                              context.getRunId(),
                                                                                              context.getSessionId(),
                                                                                              context.getUserId(),
                                                                                              "call-4",
                                                                                              "transfer-funds"));
            final List<AgentMessage> messages = invocation.getArgument(2);
            return CompletableFuture.completedFuture(ModelOutput.success(outputData("ok"),
                                                                         List.of(),
                                                                         messages,
                                                                         new ModelUsageStats()));
        };
    }

    private ExternalTool externalTool(String toolName,
                                      ExternalTool.ExternalToolResponse response) {
        final var definition = ToolDefinition.builder()
                .id(toolName)
                .name(toolName)
                .description("test tool")
                .build();
        return new ExternalTool(definition,
                                null,
                                (context, name, arguments) -> response);
    }

    private Answer<CompletableFuture<ModelOutput>> failedOutput(ErrorType errorType) {
        return invocation -> {
            final List<AgentMessage> messages = invocation.getArgument(2);
            return CompletableFuture.completedFuture(ModelOutput.error(messages,
                                                                       new ModelUsageStats(),
                                                                       SentinelError.error(errorType)));
        };
    }

    private List<SpanData> finishedSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    private Answer<CompletableFuture<ModelOutput>> orphanToolFlow() {
        return invocation -> {
            final var context = invocation.getArgument(0, com.phonepe.sentinelai.core.model.ModelRunContext.class);
            context.getAgentSetup().getEventBus().notify(new ToolCalledAgentEvent("dummy",
                                                                                  context.getRunId(),
                                                                                  context.getSessionId(),
                                                                                  context.getUserId(),
                                                                                  "call-6",
                                                                                  "dangling-tool",
                                                                                  "{}"));
            final List<AgentMessage> messages = invocation.getArgument(2);
            return CompletableFuture.completedFuture(ModelOutput.success(outputData("ok"),
                                                                         List.of(),
                                                                         messages,
                                                                         new ModelUsageStats()));
        };
    }

    private com.fasterxml.jackson.databind.JsonNode outputData(String value) {
        final var node = JsonUtils.createMapper().createObjectNode();
        node.put(Agent.OUTPUT_VARIABLE_NAME, value);
        return node;
    }

    private Answer<CompletableFuture<ModelOutput>> runWithTool(String toolName,
                                                               String toolCallId,
                                                               String arguments) {
        return invocation -> {
            final var context = invocation.getArgument(0, com.phonepe.sentinelai.core.model.ModelRunContext.class);
            final var tools = invocation.getArgument(3, Map.class);
            final var toolRunner = invocation.getArgument(4, ToolRunner.class);
            toolRunner.runTool(tools,
                               new ToolCall(context.getSessionId(),
                                            context.getRunId(),
                                            toolCallId,
                                            toolName,
                                            arguments));
            final List<AgentMessage> messages = invocation.getArgument(2);
            return CompletableFuture.completedFuture(ModelOutput.success(outputData("ok"),
                                                                         List.of(),
                                                                         messages,
                                                                         new ModelUsageStats()));
        };
    }

    private SpanData spanByName(String name) {
        return finishedSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElse(null);
    }

    private Answer<CompletableFuture<ModelOutput>> successfulOutput(String output,
                                                                    int requestTokens,
                                                                    int responseTokens) {
        return invocation -> {
            final List<AgentMessage> messages = invocation.getArgument(2);
            final var usage = new ModelUsageStats()
                    .incrementRequestTokens(requestTokens)
                    .incrementResponseTokens(responseTokens);
            return CompletableFuture.completedFuture(ModelOutput.success(outputData(output),
                                                                         List.of(),
                                                                         messages,
                                                                         usage));
        };
    }

    private SpanData waitForSpanByName(String name) {
        final var deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline) {
            final var span = spanByName(name);
            if (span != null) {
                return span;
            }
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return spanByName(name);
    }
}
