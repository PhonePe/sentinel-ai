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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.AgentEventVisitor;
import com.phonepe.sentinelai.core.events.CompactionCompletedEvent;
import com.phonepe.sentinelai.core.events.CompactionStartedEvent;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;


/**
 * OpenTelemetry tracing extension for Sentinel AI agents.
 */
@Slf4j
public class OpenTelemetryAgentExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {

    private final OpenTelemetryAgentExtensionSetup setup;

    private final ConcurrentMap<String, ActiveSpan> runSpans = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ActiveSpan> toolSpans = new ConcurrentHashMap<>();

    @Builder
    public OpenTelemetryAgentExtension(OpenTelemetryAgentExtensionSetup setup) {
        this.setup = setup;
    }

    private static String spanName(String operationName, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return operationName;
        }
        return operationName + " " + suffix;
    }

    private static long toEpochMillis(@NonNull AgentEvent event) {
        return event.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String toolSpanKey(String runId, String toolCallId) {
        return runId + "::" + toolCallId;
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(R request,
                                                         AgentRunContext<R> metadata,
                                                         A agent,
                                                         ProcessingMode processingMode) {
        return new ExtensionPromptSchema(List.of(), List.of());
    }

    @Override
    public List<FactList> facts(R request,
                                AgentRunContext<R> context,
                                A agent) {
        return List.of();
    }

    @Override
    public String name() {
        return "open-telemetry-agent-extension";
    }

    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        agent.getSetup().getEventBus().onEvent().connect(this::consumeEvent);

    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    void consumeEvent(AgentEvent event) {
        try {
            closeStaleSpans();
            event.accept(new AgentEventVisitor<>() {
                @Override
                public Void visit(CompactionCompletedEvent compactionCompleted) {
                    return null;
                }

                @Override
                public Void visit(CompactionStartedEvent compactionStarted) {
                    return null;
                }

                @Override
                public Void visit(InputReceivedAgentEvent inputReceived) {
                    onInputReceived(inputReceived);
                    return null;
                }

                @Override
                public Void visit(MessageReceivedAgentEvent messageReceived) {
                    return null;
                }

                @Override
                public Void visit(MessageSentAgentEvent messageSent) {
                    return null;
                }

                @Override
                public Void visit(OutputErrorAgentEvent outputErrorAgentEvent) {
                    onOutputError(outputErrorAgentEvent);
                    return null;
                }

                @Override
                public Void visit(OutputGeneratedAgentEvent outputGeneratedAgentEvent) {
                    onOutputGenerated(outputGeneratedAgentEvent);
                    return null;
                }

                @Override
                public Void visit(ToolCallApprovalDeniedAgentEvent toolCallApprovalDenied) {
                    onToolCallApprovalDenied(toolCallApprovalDenied);
                    return null;
                }

                @Override
                public Void visit(ToolCallCompletedAgentEvent toolCallCompleted) {
                    onToolCallCompleted(toolCallCompleted);
                    return null;
                }

                @Override
                public Void visit(ToolCalledAgentEvent toolCalled) {
                    onToolCalled(toolCalled);
                    return null;
                }
            });
        }
        catch (Exception e) {
            log.warn("Error while emitting OpenTelemetry spans for event {}: {}", event.getType(), e.getMessage());
        }
    }

    private record ActiveSpan(
            Span span,
            long startedAtEpochMillis
    ) {
    }

    private void closeDanglingToolSpans(String runId) {
        final var toolKeyPrefix = runId + "::";
        toolSpans.forEach((toolKey, activeSpan) -> {
            if (!toolKey.startsWith(toolKeyPrefix)) {
                return;
            }
            if (!toolSpans.remove(toolKey, activeSpan)) {
                return;
            }
            activeSpan.span().setStatus(StatusCode.ERROR, "Tool call did not finish before run completed");
            activeSpan.span().setAttribute(Constants.ATTR_ERROR_TYPE, Constants.TOOL_INCOMPLETE_ERROR);
            activeSpan.span().end();
        });
    }

    private void closeStaleSpans() {
        final var thresholdEpochMillis = System.currentTimeMillis() - maxActiveSpanDuration().toMillis();
        runSpans.forEach((runId, activeSpan) -> {
            if (activeSpan.startedAtEpochMillis() > thresholdEpochMillis) {
                return;
            }
            if (!runSpans.remove(runId, activeSpan)) {
                return;
            }
            activeSpan.span().setStatus(StatusCode.ERROR, "Run did not finish before span timeout");
            activeSpan.span().setAttribute(Constants.ATTR_ERROR_TYPE, Constants.RUN_INCOMPLETE_ERROR);
            activeSpan.span().end();
            closeDanglingToolSpans(runId);
        });

        toolSpans.forEach((toolKey, activeSpan) -> {
            if (activeSpan.startedAtEpochMillis() > thresholdEpochMillis) {
                return;
            }
            if (!toolSpans.remove(toolKey, activeSpan)) {
                return;
            }
            activeSpan.span().setStatus(StatusCode.ERROR, "Tool call did not finish before span timeout");
            activeSpan.span().setAttribute(Constants.ATTR_ERROR_TYPE, Constants.TOOL_INCOMPLETE_ERROR);
            activeSpan.span().end();
        });
    }

    private Duration maxActiveSpanDuration() {
        if (setup == null || setup.getMaxActiveSpanDuration() == null) {
            return Duration.ofMinutes(30);
        }
        if (setup.getMaxActiveSpanDuration().isNegative()) {
            return Duration.ZERO;
        }
        return setup.getMaxActiveSpanDuration();
    }

    private void onInputReceived(InputReceivedAgentEvent event) {
        final var spanBuilder = tracer().spanBuilder(spanName(Constants.OPERATION_INVOKE_AGENT, event.getAgentName()))
                .setStartTimestamp(toEpochMillis(event), TimeUnit.MILLISECONDS)
                .setAttribute(Constants.ATTR_OPERATION_NAME, Constants.OPERATION_INVOKE_AGENT)
                .setAttribute(Constants.ATTR_PROVIDER_NAME, providerName())
                .setAttribute(Constants.ATTR_AGENT_NAME, event.getAgentName());

        if (event.getSessionId() != null) {
            spanBuilder.setAttribute(Constants.ATTR_CONVERSATION_ID, event.getSessionId());
        }

        final var span = spanBuilder.startSpan();
        final var oldSpan = runSpans.put(event.getRunId(), new ActiveSpan(span, toEpochMillis(event)));
        if (oldSpan != null) {
            oldSpan.span().end();
        }
    }

    private void onOutputError(OutputErrorAgentEvent event) {
        final var activeSpan = runSpans.remove(event.getRunId());
        if (activeSpan == null) {
            return;
        }
        final var span = activeSpan.span();
        setError(span, event.getErrorType());
        if (event.getUsage() != null) {
            span.setAttribute(Constants.ATTR_USAGE_INPUT_TOKENS, (long) event.getUsage().getRequestTokens());
            span.setAttribute(Constants.ATTR_USAGE_OUTPUT_TOKENS, (long) event.getUsage().getResponseTokens());
        }
        span.end(toEpochMillis(event), TimeUnit.MILLISECONDS);
    }

    private void onOutputGenerated(OutputGeneratedAgentEvent event) {
        final var activeSpan = runSpans.remove(event.getRunId());
        if (activeSpan == null) {
            return;
        }
        final var span = activeSpan.span();
        if (event.getUsage() != null) {
            span.setAttribute(Constants.ATTR_USAGE_INPUT_TOKENS, (long) event.getUsage().getRequestTokens());
            span.setAttribute(Constants.ATTR_USAGE_OUTPUT_TOKENS, (long) event.getUsage().getResponseTokens());
        }
        span.end(toEpochMillis(event), TimeUnit.MILLISECONDS);
    }

    private void onToolCallApprovalDenied(ToolCallApprovalDeniedAgentEvent event) {
        final var activeSpan = toolSpans.remove(toolSpanKey(event.getRunId(), event.getToolCallId()));
        if (activeSpan == null) {
            return;
        }
        final var span = activeSpan.span();
        span.setStatus(StatusCode.ERROR, "Tool call approval denied");
        span.setAttribute(Constants.ATTR_ERROR_TYPE, Constants.TOOL_APPROVAL_DENIED_ERROR);
        span.end(toEpochMillis(event), TimeUnit.MILLISECONDS);
    }

    private void onToolCallCompleted(ToolCallCompletedAgentEvent event) {
        final var activeSpan = toolSpans.remove(toolSpanKey(event.getRunId(), event.getToolCallId()));
        if (activeSpan == null) {
            return;
        }
        final var span = activeSpan.span();
        if (event.getErrorType() != null && !ErrorType.SUCCESS.equals(event.getErrorType())) {
            setError(span, event.getErrorType());
        }
        else if (setup != null && setup.isCaptureToolCallResult() && event.getContent() != null) {
            span.setAttribute(Constants.ATTR_TOOL_CALL_RESULT, event.getContent());
        }
        span.end(toEpochMillis(event), TimeUnit.MILLISECONDS);
    }

    private void onToolCalled(ToolCalledAgentEvent event) {
        final var spanBuilder = tracer().spanBuilder(spanName(Constants.OPERATION_EXECUTE_TOOL,
                                                              event.getToolCallName()))
                .setStartTimestamp(toEpochMillis(event), TimeUnit.MILLISECONDS)
                .setAttribute(Constants.ATTR_OPERATION_NAME, Constants.OPERATION_EXECUTE_TOOL)
                .setAttribute(Constants.ATTR_TOOL_NAME, event.getToolCallName())
                .setAttribute(Constants.ATTR_TOOL_CALL_ID, event.getToolCallId());

        final var runSpan = runSpans.get(event.getRunId());
        if (runSpan != null) {
            spanBuilder.setParent(runSpan.span().storeInContext(Context.current()));
        }

        if (setup != null && setup.isCaptureToolCallArguments() && event.getArguments() != null) {
            spanBuilder.setAttribute(Constants.ATTR_TOOL_CALL_ARGUMENTS, event.getArguments());
        }

        final var span = spanBuilder.startSpan();
        final var oldSpan = toolSpans.put(toolSpanKey(event.getRunId(), event.getToolCallId()),
                                          new ActiveSpan(span, toEpochMillis(event)));
        if (oldSpan != null) {
            oldSpan.span().end();
        }
    }

    private String providerName() {
        if (setup == null || setup.getProviderName() == null || setup.getProviderName().isBlank()) {
            return OpenTelemetryAgentExtensionSetup.DEFAULT_PROVIDER_NAME;
        }
        return setup.getProviderName();
    }

    private void setError(Span span, ErrorType errorType) {
        final var type = errorType == null ? ErrorType.UNKNOWN : errorType;
        span.setStatus(StatusCode.ERROR, type.getMessage());
        span.setAttribute(Constants.ATTR_ERROR_TYPE, type.name());
    }

    private Tracer tracer() {
        if (setup == null || setup.getTracer() == null) {
            throw new IllegalStateException("OpenTelemetryAgentExtension requires setup.tracer");
        }
        return setup.getTracer();
    }
}
