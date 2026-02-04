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

import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * Provides the capability for an agent to use locally registered tools.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentCustomToolCapability extends AgentCapability {

    /**
     * Set of tools to be available to this agent
     */
    Set<String> selectedTools;

    @Builder
    @Jacksonized
    public AgentCustomToolCapability(@NonNull Set<String> selectedTools) {
        super(Type.CUSTOM_TOOLS);
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
