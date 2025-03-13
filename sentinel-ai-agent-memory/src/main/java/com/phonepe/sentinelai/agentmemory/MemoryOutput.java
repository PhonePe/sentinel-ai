package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Output for memory extraction from conversations by the model
 */
@Value
@Builder
@Jacksonized
@JsonClassDescription("Output for memory extraction from conversations by the model")
public class MemoryOutput {

    @JsonPropertyDescription("Session id for the conversation")
    String sessionId;

    @JsonPropertyDescription("User id for the conversation")
    String userId;

    @JsonPropertyDescription("Global memories extracted from the conversation. These would be  used by the agent to " +
            "personalize conversations and learn new skills. Global memories are not fixed to a session or user and " +
            "are shared across all sessions and users for an agent")
    List<GeneratedMemoryUnit> globalMemory;

    @JsonPropertyDescription("User specific memories extracted from the conversation. These would be used by the agent " +
            "to personalize conversations to the user. User memories are fixed to a user and are shared across " +
            "all sessions for the user")
    List<GeneratedMemoryUnit> userMemories;

    @JsonPropertyDescription("Session specific memories extracted from the conversation. These would be used by the agent " +
            "to personalize conversations to the session. These are contextual to the user and the session.")
    List<GeneratedMemoryUnit> sessionMemories;

}
