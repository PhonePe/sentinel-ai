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
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;

/**
 * Easy way to create capabilities for agents.
 */
@UtilityClass
public class AgentCapabilities {
    public static AgentCapability remoteHttpCalls(Map<String, Set<String>> selectedUpstreams) {
        return new AgentRemoteHttpCallCapability(selectedUpstreams);
    }

    public static AgentCapability mcpCalls(Map<String, Set<String>> selectedUpstreams) {
        return new AgentMCPCapability(selectedUpstreams);
    }

    public static AgentCapability customToolCalls(Set<String> selectedTools) {
        return new AgentCustomToolCapability(selectedTools);
    }

    public static AgentCapability inheritToolsFromParent(Set<String> selectedTools) {
        return new ParentToolInheritanceCapability(selectedTools);
    }
}
