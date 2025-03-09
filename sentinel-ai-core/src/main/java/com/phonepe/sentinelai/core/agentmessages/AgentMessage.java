package com.phonepe.sentinelai.core.agentmessages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Messages exchanged between the system and LLM
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "messageType")
@JsonSubTypes(
        {
                //Agent->LLM
                @JsonSubTypes.Type(name = "SYSTEM_PROMPT_REQUEST", value = SystemPrompt.class),
                @JsonSubTypes.Type(name = "USER_PROMPT_REQUEST", value = UserPrompt.class),
                @JsonSubTypes.Type(name = "TOOL_RUN_RESPONSE_REQUEST", value = ToolCallResponse.class),

                //LLM->Agent
                @JsonSubTypes.Type(name = "TEXT_RESPONSE", value = Text.class),
                @JsonSubTypes.Type(name = "STRUCTURED_OUTPUT_RESPONSE", value = StructuredOutput.class),
                @JsonSubTypes.Type(name = "TOOL_CALL_RESPONSE", value = ToolCall.class),
        }
)
public abstract class AgentMessage {
    private final AgentMessageType messageType;

    public abstract <T> T accept(AgentMessageVisitor<T> visitor);
}
