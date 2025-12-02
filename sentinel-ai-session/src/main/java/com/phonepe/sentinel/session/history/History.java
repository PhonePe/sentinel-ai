package com.phonepe.sentinel.session.history;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@JsonClassDescription("memory to be used by AI Agents to learn new constructs and personalize conversations with users")
@Builder
public class History {

    @JsonPropertyDescription("Session id for this session")
    String sessionId;

    @JsonPropertyDescription("List of list of messages exchanged by the agent and model in the conversation grouped by agent invocations")
    List<List<AgentMessage>> messages;

    @JsonPropertyDescription("A data bag to hold any specific metadata the client wants to hold against this conversation")
    Map<String, Object> historyMetadata;


    public List<AgentMessage> toAgentMessages() {
        return messages.stream().flatMap(List::stream).toList();
    }
}
