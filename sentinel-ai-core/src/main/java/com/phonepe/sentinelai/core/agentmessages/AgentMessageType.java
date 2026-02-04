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
