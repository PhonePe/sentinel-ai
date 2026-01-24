package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * A scrollable list of messages
 */
@Value
@Builder
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class MessageScrollable {
    List<AgentMessage> messages;
    String nextPointer;
}
