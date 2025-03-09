package com.phonepe.sentinelai.core.agentmessages;

/**
 * Different inp[ut and output message types
 */
public enum AgentMessageType {
    //Request types as sent by agent
    SYSTEM_PROMPT_REQUEST,
    USER_PROMPT_REQUEST,
    TOOL_CALL_RESPONSE_REQUEST,
    RETRY_REQUEST_REQUEST, //TODO


    //Response types as received from LLM
    TEXT_RESPONSE,
    STRUCTURED_OUTPUT_RESPONSE,
    TOOL_CALL_RESPONSE
}
