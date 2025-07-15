package com.phonepe.sentinelai.core.agentmessages;

/**
 * Different input and output message types
 */
public enum AgentMessageType {
    //Request types as sent by agent
    SYSTEM_PROMPT_REQUEST_MESSAGE,
    USER_PROMPT_REQUEST_MESSAGE,
    TOOL_CALL_REQUEST_MESSAGE,
    RETRY_REQUEST_REQUEST_MESSAGE, //TODO


    //Response types as received from LLM
    TEXT_RESPONSE_MESSAGE,
    STRUCTURED_OUTPUT_RESPONSE_MESSAGE,
    TOOL_CALL_RESPONSE_MESSAGE,

    //Generic message types
    GENERIC_TEXT_MESSAGE,
    GENERIC_RESOURCE_MESSAGE,

}
