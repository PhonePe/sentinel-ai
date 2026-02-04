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

package com.phonepe.sentinelai.configuredagents.capabilities.impl;

import com.phonepe.sentinelai.configuredagents.ConfiguredAgent;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Provides the capability for a {@link ConfiguredAgent} to make remote HTTP calls.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentRemoteHttpCallCapability extends AgentCapability {
    /**
     * A map of upstreams, vs the set of tools selected form that upstream. To select all available tools, the Set
     * can be left as empty.
     */
    Map<String, Set<String>> selectedTools;

    @Builder
    @Jacksonized
    public AgentRemoteHttpCallCapability(@NonNull Map<String, Set<String>> selectedTools) {
        super(Type.REMOTE_HTTP_CALLS);
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
