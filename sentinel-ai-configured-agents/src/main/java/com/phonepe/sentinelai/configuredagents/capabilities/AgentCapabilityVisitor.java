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

package com.phonepe.sentinelai.configuredagents.capabilities;

import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentCustomToolCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMemoryCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentSessionManagementCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;

/**
 * To handle capability specific behavior in a type-safe manner,
 */
public interface AgentCapabilityVisitor<T> {
    T visit(AgentRemoteHttpCallCapability remoteHttpCallCapability);

    T visit(AgentMCPCapability mcpCapability);

    T visit(AgentCustomToolCapability customToolCapability);

    T visit(AgentMemoryCapability memoryCapability);

    T visit(AgentSessionManagementCapability sessionManagementCapability);

    T visit(ParentToolInheritanceCapability parentToolInheritanceCapability);
}
