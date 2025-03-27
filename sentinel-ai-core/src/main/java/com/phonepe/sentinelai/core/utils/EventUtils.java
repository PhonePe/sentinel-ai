package com.phonepe.sentinelai.core.utils;

import com.google.common.base.Stopwatch;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@UtilityClass
public class EventUtils {
    public static <R, T, A extends Agent<R, T, A>> void raiseMessageReceivedEvent(
            AgentRunContext<R> context,
            A agent,
            AgentResponse newMessage,
            Stopwatch stopwatch) {
        context.getAgentSetup()
                .getEventBus()
                .notify(new MessageReceivedAgentEvent(agent.name(),
                                                      context.getRunId(),
                                                      AgentUtils.sessionId(context),
                                                      AgentUtils.userId(context),
                                                      newMessage,
                                                      Duration.ofMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS))));
    }

    public static <R, T, A extends Agent<R, T, A>> void raiseMessageSentEvent(
            AgentRunContext<R> context,
            A agent,
            List<AgentMessage> oldMessages) {
        context.getAgentSetup()
                .getEventBus()
                .notify(new MessageSentAgentEvent(agent.name(),
                                                  context.getRunId(),
                                                  AgentUtils.sessionId(context),
                                                  AgentUtils.userId(context),
                                                  List.copyOf(oldMessages)));
    }
}
