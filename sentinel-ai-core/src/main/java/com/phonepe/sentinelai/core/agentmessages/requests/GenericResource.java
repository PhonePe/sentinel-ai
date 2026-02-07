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

package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GenericResource extends AgentGenericMessage {
    public enum ResourceType {
        TEXT,
        BLOB
    }

    ResourceType resourceType;
    String uri;
    String mimeType;

    String content;
    String serializedJson;

    @SuppressWarnings("java:S107")
    public GenericResource(String sessionId,
                           String runId,
                           Role role,
                           ResourceType resourceType,
                           String uri,
                           String mimeType,
                           String content,
                           String serializedJson) {
        this(sessionId,
             runId,
             null,
             null,
             role,
             resourceType,
             uri,
             mimeType,
             content,
             serializedJson);
    }

    @Builder
    @Jacksonized
    public GenericResource(String sessionId,
                           String runId,
                           String messageId,
                           Long timestamp,
                           Role role,
                           ResourceType resourceType,
                           String uri,
                           String mimeType,
                           String content,
                           String serializedJson) {
        super(sessionId,
              runId,
              messageId,
              timestamp,
              AgentMessageType.GENERIC_RESOURCE_MESSAGE,
              role);
        this.resourceType = resourceType;
        this.uri = uri;
        this.mimeType = mimeType;
        this.content = content;
        this.serializedJson = serializedJson;
    }

    @Override
    public <T> T accept(AgentGenericMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
