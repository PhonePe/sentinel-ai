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

package com.phonepe.sentinelai.models.wire;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

/**
 * Translates one immutable {@link AgentMessage} into provider wire JSON.
 * <p>
 * Implementations must be pure: the same message always translates to the same JSON. The model
 * loop translates each message once per run start and reuses the nodes across turns; a memoization
 * cache on top of this interface is a planned follow-up and is deliberately not part of this SPI.
 */
@FunctionalInterface
public interface MessageCodec {

    /**
     * Translates one message to its wire JSON representation.
     *
     * @param message Immutable message to translate.
     * @return Wire JSON node for the message.
     */
    com.fasterxml.jackson.databind.JsonNode translate(AgentMessage message);
}
