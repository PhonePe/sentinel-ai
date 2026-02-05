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

package com.phonepe.sentinelai.configuredagents;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.AgentUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link InMemoryAgentConfigurationSource}.
 */
class InMemoryAgentConfigurationSourceTest {

    @Test
    void testSaveAndRead() {
        final var source = new InMemoryAgentConfigurationSource();
        final var config = new AgentConfiguration("Test Agent",
                                                  "Agent for testing",
                                                  "You are a test agent.",
                                                  null,
                                                  null,
                                                  List.of(),
                                                  null);
        String agentId = AgentUtils.id(config.getAgentName());

        // Save the agent configuration
        final var savedMetadata = source.save(agentId, config);
        assertTrue(savedMetadata.isPresent());
        assertEquals(agentId, savedMetadata.get().getId());
        assertEquals(config, savedMetadata.get().getConfiguration());

        // Read the agent configuration
        var readMetadata = source.read(agentId);
        assertTrue(readMetadata.isPresent());
        assertEquals(agentId, readMetadata.get().getId());
        assertEquals(config, readMetadata.get().getConfiguration());

        //Delete configuration
        assertTrue(source.remove(agentId));
        assertFalse(source.read(agentId).isPresent());
        assertFalse(source.remove("invalid-id"));

        //Assert that find does not work
        assertThrows(UnsupportedOperationException.class,
                     () -> source.find("Test"));
    }
}
