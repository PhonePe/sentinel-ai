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

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenTelemetryAgentExtensionTest {

    private static final AttributeKey<String> ATTR_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> ATTR_PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    private static final AttributeKey<String> ATTR_AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    private static final AttributeKey<String> ATTR_CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    private static final AttributeKey<String> ATTR_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> ATTR_TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call.id");
    private static final AttributeKey<String> ATTR_TOOL_CALL_ARGUMENTS = AttributeKey.stringKey("gen_ai.tool.call.arguments");
    private static final AttributeKey<String> ATTR_TOOL_CALL_RESULT = AttributeKey.stringKey("gen_ai.tool.call.result");
    private static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");

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

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        openTelemetrySdk.close();
    }

    @Test
    void shouldEmitInvokeAgentSpanOnInputAndOutputEvents() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                               .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                               .providerName("openai")
                               .build())
                .build();

        extension.consumeEvent(new InputReceivedAgentEvent("support-agent",
                                                           "run-1",
                                                           "session-1",
                                                           "user-1",
                                                           "{\"input\":\"hello\"}"));

        final var usage = new ModelUsageStats().incrementRequestTokens(120).incrementResponseTokens(40);
        extension.consumeEvent(new OutputGeneratedAgentEvent("support-agent",
                                                             "run-1",
                                                             "session-1",
                                                             "user-1",
                                                             "{\"output\":\"hi\"}",
                                                             usage,
                                                             Duration.ofMillis(50)));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());

        final var span = spans.get(0);
        assertEquals("invoke_agent support-agent", span.getName());
        assertEquals("invoke_agent", span.getAttributes().get(ATTR_OPERATION_NAME));
        assertEquals("openai", span.getAttributes().get(ATTR_PROVIDER_NAME));
        assertEquals("support-agent", span.getAttributes().get(ATTR_AGENT_NAME));
        assertEquals("session-1", span.getAttributes().get(ATTR_CONVERSATION_ID));
    }

    @Test
    void shouldEmitExecuteToolSpanWithErrorForFailedToolCall() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                               .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                               .captureToolCallArguments(true)
                               .captureToolCallResult(true)
                               .build())
                .build();

        extension.consumeEvent(new ToolCalledAgentEvent("support-agent",
                                                        "run-2",
                                                        "session-2",
                                                        "user-2",
                                                        "call-2",
                                                        "lookup-order",
                                                        "{\"orderId\":\"o-1\"}"));

        extension.consumeEvent(new ToolCallCompletedAgentEvent("support-agent",
                                                               "run-2",
                                                               "session-2",
                                                               "user-2",
                                                               "call-2",
                                                               "lookup-order",
                                                               ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                               "tool failed",
                                                               Duration.ofMillis(100)));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());

        final var span = spans.get(0);
        assertEquals("execute_tool lookup-order", span.getName());
        assertEquals("execute_tool", span.getAttributes().get(ATTR_OPERATION_NAME));
        assertEquals("lookup-order", span.getAttributes().get(ATTR_TOOL_NAME));
        assertEquals("call-2", span.getAttributes().get(ATTR_TOOL_CALL_ID));
        assertEquals("{\"orderId\":\"o-1\"}", span.getAttributes().get(ATTR_TOOL_CALL_ARGUMENTS));
        assertEquals("TOOL_CALL_PERMANENT_FAILURE", span.getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldEmitExecuteToolSpanWithResultForSuccessfulToolCall() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                               .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                               .captureToolCallResult(true)
                               .build())
                .build();

        extension.consumeEvent(new ToolCalledAgentEvent("support-agent",
                                                        "run-3",
                                                        "session-3",
                                                        "user-3",
                                                        "call-3",
                                                        "get-weather",
                                                        "{\"city\":\"Paris\"}"));

        extension.consumeEvent(new ToolCallCompletedAgentEvent("support-agent",
                                                               "run-3",
                                                               "session-3",
                                                               "user-3",
                                                               "call-3",
                                                               "get-weather",
                                                               ErrorType.SUCCESS,
                                                               "{\"temp\":22}",
                                                               Duration.ofMillis(90)));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());
        assertEquals("{\"temp\":22}", spans.get(0).getAttributes().get(ATTR_TOOL_CALL_RESULT));
    }

    @Test
    void shouldMarkToolSpanErrorWhenApprovalIsDenied() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                               .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                               .build())
                .build();

        extension.consumeEvent(new ToolCalledAgentEvent("support-agent",
                                                        "run-4",
                                                        "session-4",
                                                        "user-4",
                                                        "call-4",
                                                        "transfer-funds",
                                                        "{\"amount\":5000}"));

        extension.consumeEvent(new ToolCallApprovalDeniedAgentEvent("support-agent",
                                                                     "run-4",
                                                                     "session-4",
                                                                     "user-4",
                                                                     "call-4",
                                                                     "transfer-funds"));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());
        assertEquals("tool_call_approval_denied", spans.get(0).getAttributes().get(ATTR_ERROR_TYPE));
    }

    @Test
    void shouldMarkInvokeAgentSpanErrorWhenOutputFails() {
        final var extension = OpenTelemetryAgentExtension.<String, String, DummyAgent>builder()
                .setup(OpenTelemetryAgentExtensionSetup.builder()
                               .tracer(openTelemetrySdk.getTracer("sentinel.test"))
                               .build())
                .build();

        extension.consumeEvent(new InputReceivedAgentEvent("support-agent",
                                                           "run-5",
                                                           "session-5",
                                                           "user-5",
                                                           "{\"input\":\"hello\"}"));

        extension.consumeEvent(new OutputErrorAgentEvent("support-agent",
                                                         "run-5",
                                                         "session-5",
                                                         "user-5",
                                                         ErrorType.LENGTH_EXCEEDED,
                                                         new ModelUsageStats(),
                                                         "failed",
                                                         Duration.ofMillis(30)));

        final var spans = finishedSpans();
        assertEquals(1, spans.size());
        assertEquals("LENGTH_EXCEEDED", spans.get(0).getAttributes().get(ATTR_ERROR_TYPE));
    }

    private List<SpanData> finishedSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    private abstract static class DummyAgent extends Agent<String, String, DummyAgent> {
        protected DummyAgent() {
            super(String.class,
                  "dummy",
                  null,
                  List.of(),
                  null);
        }

        @Override
        public String name() {
            return "dummy";
        }
    }
}

