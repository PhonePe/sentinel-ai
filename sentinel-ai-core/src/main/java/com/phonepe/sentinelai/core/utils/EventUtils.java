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

package com.phonepe.sentinelai.core.utils;

import com.google.common.base.Stopwatch;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@UtilityClass
@Slf4j
public class EventUtils {

    public static void raiseInputReceivedEvent(String agentName,
                                               AgentRunContext<?> context,
                                               Object content,
                                               AgentSetup agentSetup) {
        try {
            agentSetup.getEventBus()
                    .notify(new InputReceivedAgentEvent(agentName,
                                                        context.getRunId(),
                                                        AgentUtils.sessionId(
                                                                             context),
                                                        AgentUtils.userId(
                                                                          context),
                                                        agentSetup.getMapper()
                                                                .writeValueAsString(content)));
        }
        catch (Exception e) {
            log.error("Error while raising input received event for agent: {}, runId: {}",
                      agentName,
                      context.getRunId(),
                      AgentUtils.rootCause(e).getMessage());
        }
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageReceivedEvent(AgentRunContext<R> context,
                                                                                  A agent,
                                                                                  AgentResponse newMessage,
                                                                                  Stopwatch stopwatch) {
        raiseMessageReceivedEvent(agent.name(),
                                  context.getRunId(),
                                  AgentUtils.sessionId(context),
                                  AgentUtils.userId(context),
                                  context.getAgentSetup(),
                                  newMessage,
                                  stopwatch);
    }

    public static void raiseMessageReceivedEvent(ModelRunContext modelRunContext,
                                                 AgentResponse newMessage,
                                                 Stopwatch stopwatch) {
        raiseMessageReceivedEvent(modelRunContext.getAgentName(),
                                  modelRunContext.getRunId(),
                                  modelRunContext.getSessionId(),
                                  modelRunContext.getUserId(),
                                  modelRunContext.getAgentSetup(),
                                  newMessage,
                                  stopwatch);
    }


    public static void raiseMessageReceivedEvent(String agentName,
                                                 String runId,
                                                 String sessionId,
                                                 String userId,
                                                 AgentSetup agentSetup,
                                                 AgentResponse newMessage,
                                                 Stopwatch stopwatch) {
        agentSetup.getEventBus()
                .notify(new MessageReceivedAgentEvent(agentName,
                                                      runId,
                                                      sessionId,
                                                      userId,
                                                      newMessage,
                                                      Duration.ofMillis(stopwatch
                                                              .elapsed(TimeUnit.MILLISECONDS))));
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageSentEvent(AgentRunContext<R> context,
                                                                              A agent,
                                                                              List<AgentMessage> oldMessages) {
        raiseMessageSentEvent(agent.name(),
                              context.getRunId(),
                              AgentUtils.sessionId(context),
                              AgentUtils.userId(context),
                              context.getAgentSetup(),
                              oldMessages,
                              oldMessages.isEmpty() ? null : oldMessages.get(oldMessages.size() - 1));
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageSentEvent(ModelRunContext modelRunContext,
                                                                              List<AgentMessage> oldMessages,
                                                                              AgentMessage currentMessage) {
        raiseMessageSentEvent(modelRunContext.getAgentName(),
                              modelRunContext.getRunId(),
                              modelRunContext.getSessionId(),
                              modelRunContext.getUserId(),
                              modelRunContext.getAgentSetup(),
                              oldMessages,
                              currentMessage);
    }

    public static void raiseMessageSentEvent(String agentName,
                                             String runId,
                                             String sessionId,
                                             String userId,
                                             AgentSetup agentSetup,
                                             List<AgentMessage> oldMessages,
                                             AgentMessage currentMessage) {
        agentSetup.getEventBus()
                .notify(new MessageSentAgentEvent(agentName,
                                                  runId,
                                                  sessionId,
                                                  userId,
                                                  List.copyOf(oldMessages),
                                                  currentMessage));
    }

    public static void raiseOutputEvent(ModelRunContext context,
                                        ModelOutput output,
                                        Stopwatch stopwatch) {
        AgentEvent event;
        try {
            if (output.getError() == null || output.getError()
                    .getErrorType()
                    .equals(ErrorType.SUCCESS)) {
                event = new OutputGeneratedAgentEvent(context.getAgentName(),
                                                      context.getRunId(),
                                                      context.getSessionId(),
                                                      context.getUserId(),
                                                      context.getAgentSetup()
                                                              .getMapper()
                                                              .writeValueAsString(output
                                                                      .getData()),
                                                      output.getUsage(),
                                                      Duration.ofMillis(stopwatch
                                                              .elapsed(TimeUnit.MILLISECONDS)));
            }
            else {
                event = new OutputErrorAgentEvent(context.getAgentName(),
                                                  context.getRunId(),
                                                  context.getSessionId(),
                                                  context.getUserId(),
                                                  output.getError()
                                                          .getErrorType(),
                                                  output.getUsage(),
                                                  output.getError()
                                                          .getMessage(),
                                                  Duration.ofMillis(stopwatch
                                                          .elapsed(TimeUnit.MILLISECONDS)));
            }
            context.getAgentSetup().getEventBus().notify(event);
        }
        catch (Exception e) {
            log.error("Error while raising output event for agent: {}, runId: {}",
                      context.getAgentName(),
                      context.getRunId(),
                      AgentUtils.rootCause(e).getMessage());
        }
    }

}
