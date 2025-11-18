package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Output of a pre-processor of agent messages.
 *
 * If the transformedMessages is set as null or empty list by a processor, it is a NOOP and the agent messages
 * will not be modified.
 */
@Value
@Builder
public class AgentMessagesPreProcessResult {
    List<AgentMessage> transformedMessages;
}
