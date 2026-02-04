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

import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentCapabilities}
 */
class AgentCapabilitiesTest {

    @Test
    void test() {
        final var remoteHttpCalls = AgentCapabilities.remoteHttpCalls(Map.of("upstream1", Set.of("http://example.com")));
        assertNotNull(remoteHttpCalls);
        assertInstanceOf(AgentRemoteHttpCallCapability.class, remoteHttpCalls);
        assertThrowsExactly(NullPointerException.class, () -> AgentCapabilities.remoteHttpCalls(null));
        final var mcpCalls = AgentCapabilities.mcpCalls(Map.of("upstream2", Set.of("mcp://example.com")));
        assertNotNull(mcpCalls);
        assertInstanceOf(AgentMCPCapability.class, mcpCalls);
        assertThrowsExactly(NullPointerException.class, () -> AgentCapabilities.mcpCalls(null));
    }

}