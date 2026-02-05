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
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.model.ModelRunContext;

import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@UtilityClass
public class EventUtils {

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageReceivedEvent(AgentRunContext<R> context, A agent,
            AgentResponse newMessage, Stopwatch stopwatch) {
        raiseMessageReceivedEvent(agent.name(), context.getRunId(), AgentUtils.sessionId(context), AgentUtils.userId(
                context), context.getAgentSetup(), newMessage, stopwatch);
    }

    public static void raiseMessageReceivedEvent(ModelRunContext modelRunContext, AgentResponse newMessage,
            Stopwatch stopwatch) {
        raiseMessageReceivedEvent(modelRunContext.getAgentName(), modelRunContext.getRunId(), modelRunContext
                .getSessionId(), modelRunContext.getUserId(), modelRunContext.getAgentSetup(), newMessage, stopwatch);
    }

    public static void raiseMessageReceivedEvent(String agentName, String runId, String sessionId, String userId,
            AgentSetup agentSetup, AgentResponse newMessage, Stopwatch stopwatch) {
        agentSetup.getEventBus()
                .notify(new MessageReceivedAgentEvent(agentName, runId, sessionId, userId, newMessage, Duration
                        .ofMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS))));
    }


    public static <R, T, A extends Agent<R, T, A>> void raiseMessageSentEvent(AgentRunContext<R> context, A agent,
            List<AgentMessage> oldMessages) {
        raiseMessageSentEvent(agent.name(), context.getRunId(), AgentUtils.sessionId(context), AgentUtils.userId(
                context), context.getAgentSetup(), oldMessages);
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageSentEvent(ModelRunContext modelRunContext,
            List<AgentMessage> oldMessages) {
        raiseMessageSentEvent(modelRunContext.getAgentName(), modelRunContext.getRunId(), modelRunContext
                .getSessionId(), modelRunContext.getUserId(), modelRunContext.getAgentSetup(), oldMessages);
    }

    public static void raiseMessageSentEvent(String agentName, String runId, String sessionId, String userId,
            AgentSetup agentSetup, List<AgentMessage> oldMessages) {
        agentSetup.getEventBus()
                .notify(new MessageSentAgentEvent(agentName, runId, sessionId, userId, List.copyOf(oldMessages)));
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseOutputGeneratedEvent(AgentRunContext<R> context, A agent,
            String content, Stopwatch stopwatch) {
        context.getAgentSetup()
                .getEventBus()
                .notify(new OutputGeneratedAgentEvent(agent.name(), context.getRunId(), AgentUtils.sessionId(context),
                        AgentUtils.userId(context), content, Duration.ofMillis(stopwatch.elapsed(
                                TimeUnit.MILLISECONDS))));
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseOutputGeneratedEvent(ModelRunContext context,
            String content, Stopwatch stopwatch) {
        context.getAgentSetup()
                .getEventBus()
                .notify(new OutputGeneratedAgentEvent(context.getAgentName(), context.getRunId(), context
                        .getSessionId(), context.getUserId(), content, Duration.ofMillis(stopwatch.elapsed(
                                TimeUnit.MILLISECONDS))));
    }
}
