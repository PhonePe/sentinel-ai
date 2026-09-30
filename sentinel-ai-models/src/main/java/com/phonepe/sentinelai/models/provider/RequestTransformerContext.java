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

package com.phonepe.sentinelai.models.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.models.wire.WireContext;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * Context a {@link RequestTransformer} sees for one model call. It adds run-level data (session
 * id, agent name, the agent messages and the translated wire messages of this turn) on top of the
 * {@link WireContext}.
 */
@Value
@Builder
public class RequestTransformerContext {

    /**
     * Wire context of the call.
     */
    @NonNull
    WireContext wireContext;

    /**
     * Session id of this run; empty when the run has no session.
     */
    @Getter(AccessLevel.NONE)
    String sessionId;

    /**
     * Name of the agent that runs the model; empty when not known.
     */
    @Getter(AccessLevel.NONE)
    String agentName;

    /**
     * Agent messages of this turn; the same list the protocol translates into the wire messages.
     */
    @NonNull
    @Builder.Default
    List<AgentMessage> messages = List.of();

    /**
     * Translated wire messages of this turn; the same list embedded in the request body.
     */
    @NonNull
    @Builder.Default
    List<JsonNode> wireMessages = List.of();

    /**
     * Returns the name of the agent that runs the model.
     *
     * @return The agent name, or empty when not known.
     */
    public Optional<String> agentName() {
        return Optional.ofNullable(agentName);
    }

    /**
     * Returns the mapper of the agent setup; same instance as {@link WireContext#getMapper()}.
     *
     * @return The object mapper.
     */
    public ObjectMapper mapper() {
        return wireContext.getMapper();
    }

    /**
     * Returns the session id of this run.
     *
     * @return The session id, or empty when the run has no session.
     */
    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }
}
