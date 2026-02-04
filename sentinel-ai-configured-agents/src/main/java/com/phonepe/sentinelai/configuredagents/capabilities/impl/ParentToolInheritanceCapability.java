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

import com.google.common.base.Preconditions;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * Inherits all or the provided set of tools from the parent agent.
 * If capability is provided and selected tools not specified, all parent tools are inherited.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ParentToolInheritanceCapability extends AgentCapability {

    Set<String> selectedTools;

    @Builder
    @Jacksonized
    public ParentToolInheritanceCapability(Set<String> selectedTools) {
        super(Type.TOOL_INHERITANCE);
        Preconditions.checkArgument(null != selectedTools && !selectedTools.isEmpty(),
                "Specific tools must be provided for tool inheritance capability");
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
