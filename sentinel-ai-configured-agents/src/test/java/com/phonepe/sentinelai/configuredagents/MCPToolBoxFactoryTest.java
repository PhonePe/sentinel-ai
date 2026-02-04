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

import com.phonepe.sentinelai.core.utils.JsonUtils;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link MCPToolBoxFactory}.
 */
class MCPToolBoxFactoryTest {

    @Test
    void test() {
        try(final var mcpClient = mock(McpSyncClient.class)) {
            final var factory = MCPToolBoxFactory.builder()
                    .objectMapper(JsonUtils.createMapper())
                    .clientProvider(upstream -> {
                        if ("testUpstream".equals(upstream)) {
                            return Optional.of(mcpClient);
                        }
                        return Optional.empty();
                    })
                    .build();

            assertTrue(factory.create("testUpstream").isPresent());
            assertFalse(factory.create("invalidUpstream").isPresent());
        }
    }
}