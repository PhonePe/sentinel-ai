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

package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Primitives;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExecutableToolVisitor;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.tools.ToolMethodInfo;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.Policy;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Slf4j
@Value
public class AgentToolRunner<R, T, A extends Agent<R, T, A>> implements ToolRunner {
    private static final Set<Class<? extends Exception>> UNHANDLED_EXCEPTION_TYPES = Set
            .of(
                NullPointerException.class,
                IllegalAccessException.class,
                JsonProcessingException.class);

    A agent;
    AgentSetup setup;
    ToolRunApprovalSeeker<R, T, A> toolRunApprovalSeeker;
    AgentRunContext<R> context;

    private static void printToolCallError(ToolCall toolCall, Object error) {
        log.error("Error calling external tool {} -> {}: {}",
                  toolCall.getToolCallId(),
                  toolCall.getToolName(),
                  error);
    }

    /**
     * This returns a temporary failure for unhandled exceptions. Can be used to retry if needed.
     *
     * @param toolCall Tool call being executed
     * @param e        Exception being handled
     * @return
     */
    private static ToolCallResponse processUnhandledException(AgentRunContext<?> context,
                                                              ToolCall toolCall,
                                                              Exception e) {
        final var errorMessage = AgentUtils.rootCause(e).getMessage();
        printToolCallError(toolCall, errorMessage);
        if (log.isDebugEnabled()) {
            log.error("Error stacktrace for %s".formatted(toolCall
                    .getToolCallId()), e);
        }
        return new ToolCallResponse(AgentUtils.sessionId(context),
                                    context.getRunId(),
                                    toolCall.getToolCallId(),
                                    toolCall.getToolName(),
                                    ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                    "Error running tool: %s".formatted(
                                                                       errorMessage),
                                    LocalDateTime.now());
    }

    private static <R> ToolCallResponse runExternalTool(ExternalTool externalTool,
                                                        ToolCall toolCall,
                                                        AgentRunContext<R> context) {
        try {
            log.debug("Calling external tool: {} [{}] Arguments: {}",
                      toolCall.getToolCallId(),
                      toolCall.getToolName(),
                      toolCall.getArguments());
            final var response = externalTool.getCallable()
                    .apply(context,
                           toolCall.getToolName(),
                           toolCall.getArguments());
            log.debug("Tool response: {}", response);
            final var error = response.error();
            if (!error.equals(ErrorType.SUCCESS)) {
                printToolCallError(toolCall, response.response());
                return new ToolCallResponse(AgentUtils.sessionId(context),
                                            context.getRunId(),
                                            toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            error,
                                            "Tool call failed. External tool error: %s"
                                                    .formatted(Objects.toString(
                                                                                response.response())),
                                            LocalDateTime.now());
            }
            return successResponse(response, toolCall, context);
        }
        catch (Exception e) {
            return processUnhandledException(context, toolCall, e);
        }
    }

    private static <R> ToolCallResponse successResponse(ExternalTool.ExternalToolResponse response,
                                                        ToolCall toolCall,
                                                        AgentRunContext<R> context) {
        try {
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.SUCCESS,
                                        context.getAgentSetup()
                                                .getMapper()
                                                .writeValueAsString(response
                                                        .response()),
                                        LocalDateTime.now());
        }
        catch (JsonProcessingException e) {
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.SERIALIZATION_ERROR,
                                        "Error serializing external tool response: %s"
                                                .formatted(Objects.toString(
                                                                            response.response())),
                                        LocalDateTime.now());
        }
    }

    @Override
    public ToolCallResponse runTool(Map<String, ExecutableTool> tools,
                                    ToolCall toolCall) {
        final var eventBus = context.getAgentSetup().getEventBus();
        if (!toolRunApprovalSeeker.seekApproval(agent, context, toolCall)) {
            log.info("Tool call {} for tool {} was not approved by the user",
                     toolCall.getToolCallId(),
                     toolCall.getToolName());
            eventBus.notify(new ToolCallApprovalDeniedAgentEvent(agent.name(),
                                                                 context.getRunId(),
                                                                 AgentUtils
                                                                         .sessionId(context),
                                                                 AgentUtils
                                                                         .userId(context),
                                                                 toolCall.getToolCallId(),
                                                                 toolCall.getToolName()));
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                        "Tool call was not approved by the user",
                                        LocalDateTime.now());
        }
        eventBus.notify(new ToolCalledAgentEvent(agent.name(),
                                                 context.getRunId(),
                                                 AgentUtils.sessionId(context),
                                                 AgentUtils.userId(context),
                                                 toolCall.getToolCallId(),
                                                 toolCall.getToolName(),
                                                 toolCall.getArguments()));
        final var stopwatch = Stopwatch.createStarted();
        final var response = runTool(context, tools, toolCall);
        eventBus.notify(new ToolCallCompletedAgentEvent(agent.name(),
                                                        context.getRunId(),
                                                        AgentUtils.sessionId(
                                                                             context),
                                                        AgentUtils.userId(
                                                                          context),
                                                        toolCall.getToolCallId(),
                                                        toolCall.getToolName(),
                                                        response.getErrorType(),
                                                        response.getResponse(),
                                                        Duration.ofMillis(stopwatch
                                                                .elapsed(TimeUnit.MILLISECONDS))));
        return response;
    }


    /**
     * Convert parameters string received from LLM to actual parameters for tool call
     *
     * @param methodInfo Method information for the tool
     * @param params     Parameters string to be converted
     * @return List of parameters to be passed to the tool/function
     */
    @SneakyThrows
    private List<Object> params(ToolMethodInfo methodInfo, String params) {
        final var objectMapper = setup.getMapper();
        return ToolUtils.convertToRealParams(methodInfo, params, objectMapper);
    }

    @SuppressWarnings("java:S3011")
    private ToolCallResponse runInternalTool(InternalTool internalTool,
                                             AgentRunContext<R> context,
                                             ToolCall toolCall) {
        try {
            final var args = new ArrayList<>();
            if (internalTool.getToolDefinition().isContextAware()) {
                args.add(context);
            }
            args.addAll(params(internalTool.getMethodInfo(),
                               toolCall.getArguments()));
            final var callable = internalTool.getMethodInfo().callable();
            callable.setAccessible(true);
            log.debug("Calling internal tool: {} [{}] Arguments: {}",
                      toolCall.getToolCallId(),
                      toolCall.getToolName(),
                      toolCall.getArguments());
            var resultObject = callable.invoke(internalTool.getInstance(),
                                               args.toArray());
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.SUCCESS,
                                        toStringContent(internalTool,
                                                        resultObject),
                                        LocalDateTime.now());
        }
        catch (InvocationTargetException e) {
            log.error("Local error making tool call " + toolCall
                    .getToolCallId(), e);
            final var rootCause = AgentUtils.rootCause(e);
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                        "Tool call local failure: %s".formatted(
                                                                                rootCause
                                                                                        .getMessage()),
                                        LocalDateTime.now());
        }
        catch (Exception e) {
            return processUnhandledException(context, toolCall, e);
        }
    }

    private ToolCallResponse runTool(AgentRunContext<R> context,
                                     Map<String, ExecutableTool> tools,
                                     ToolCall toolCall) {
        final var tool = tools.get(toolCall.getToolName());
        if (null == tool) {
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                        ("Tool call %s failed. There is no tool with name: %s. Retry by calling any of the following available tools with the appropriate parameters: %s.")
                                                .formatted(toolCall
                                                        .getToolCallId(),
                                                           toolCall.getToolName(),
                                                           Set.copyOf(tools
                                                                   .keySet())),
                                        LocalDateTime.now());
        }
        final var toolDefinition = tool.getToolDefinition();
        final var policies = new ArrayList<Policy<ToolCallResponse>>();
        final var attempts = 1 + Math.max(ToolDefinition.NO_RETRY,
                                          toolDefinition.getRetries());
        policies.add(RetryPolicy.<ToolCallResponse>builder()
                .handleIf((response, error) -> {
                    if (response != null && response.getErrorType() != null) {
                        return response.getErrorType()
                                .equals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE) || response
                                        .getErrorType()
                                        .equals(ErrorType.FORCED_RETRY);
                    }
                    return error != null && !UNHANDLED_EXCEPTION_TYPES.contains(
                                                                                AgentUtils
                                                                                        .rootCause(error)
                                                                                        .getClass());

                })
                .withMaxAttempts(attempts)
                .build());
        if (toolDefinition.getTimeoutSeconds() != ToolDefinition.NO_TIMEOUT) {
            final var timeoutSeconds = Duration.ofSeconds(toolDefinition
                    .getTimeoutSeconds());
            policies.add(Timeout.<ToolCallResponse>builder(timeoutSeconds)
                    .withInterrupt()
                    .build());
        }

        try {
            return Failsafe.with(policies)
                    .get(() -> tool.accept(new ExecutableToolVisitor<>() {
                        @Override
                        public ToolCallResponse visit(ExternalTool externalTool) {
                            return runExternalTool(externalTool,
                                                   toolCall,
                                                   context);
                        }

                        @Override
                        public ToolCallResponse visit(InternalTool internalTool) {
                            return runInternalTool(internalTool,
                                                   context,
                                                   toolCall);

                        }
                    }));
        }
        catch (TimeoutExceededException e) {
            log.error("Tool call {} -> {} timed out after {} seconds",
                      toolCall.getToolCallId(),
                      toolCall.getToolName(),
                      toolDefinition.getTimeoutSeconds(),
                      e);
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_TIMEOUT,
                                        "Tool call timed out after %d seconds"
                                                .formatted(toolDefinition
                                                        .getTimeoutSeconds()),
                                        LocalDateTime.now());
        }
        catch (FailsafeException e) {
            log.error("Error calling tool {} -> {} after {} attempts",
                      toolCall.getToolCallId(),
                      toolCall.getToolName(),
                      attempts,
                      e);
            final var cause = AgentUtils.rootCause(e);
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                        "Error calling tool after %d attempts: %s"
                                                .formatted(attempts,
                                                           cause.getMessage()),
                                        LocalDateTime.now());
        }
        catch (Exception e) {
            log.error("Error calling tool {} -> {} after {} attempts",
                      toolCall.getToolCallId(),
                      toolCall.getToolName(),
                      attempts,
                      e);
            return new ToolCallResponse(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                        "Error calling tool after %d attempts: %s"
                                                .formatted(attempts,
                                                           AgentUtils.rootCause(
                                                                                e)
                                                                   .getMessage()),
                                        LocalDateTime.now());
        }

    }

    /**
     * Convert tool response to string to send to LLM. For void return type a fixed success string is sent to LLM.
     *
     * @param tool   Tool being called, we use this to derive the return type
     * @param result Actual result from the tool
     * @return JSON serialized result
     */
    @SneakyThrows
    private String toStringContent(InternalTool tool, Object result) {
        final var returnType = tool.getMethodInfo().returnType();
        if (returnType.equals(Void.TYPE)) {
            return "success"; //This is recommended by OpenAI
        }
        else {
            if (returnType.isAssignableFrom(String.class) || Primitives
                    .isWrapperType(returnType)) {
                return Objects.toString(result);
            }
        }
        return setup.getMapper().writeValueAsString(result);
    }

}
