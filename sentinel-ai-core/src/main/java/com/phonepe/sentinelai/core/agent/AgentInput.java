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

package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Input to an agent
 */
@Value
@AllArgsConstructor
@Builder
@With
public class AgentInput<R> {
    /**
     * Request object
     */
    @NonNull
    R request;

    /**
     * List of facts to be passed to the agent. This is passed to LLM as 'knowledge' in system prompt.
     */
    List<FactList> facts;
    /**
     * Metadata for the request
     */
    AgentRequestMetadata requestMetadata;
    /**
     * Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     * messages are generated and consumed by the agent in this session.
     */
    List<AgentMessage> oldMessages;
    /**
     * Setup for the agent. This is an override at runtime. If set to null, the setup provided will be used. Whatever
     * fields are provided are merged with the setup provided during agent creation. If same fields are provided in
     * both places, the ones provided at runtime will get precedence.
     */
    AgentSetup agentSetup;
}
