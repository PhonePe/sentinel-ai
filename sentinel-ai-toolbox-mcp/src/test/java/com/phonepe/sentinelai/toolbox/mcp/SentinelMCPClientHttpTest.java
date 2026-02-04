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

package com.phonepe.sentinelai.toolbox.mcp;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link ComposingMCPToolBox} with HTTP transport
 */
@Testcontainers
class SentinelMCPClientHttpTest {
    @Container
    static GenericContainer<?> container = new GenericContainer<>("tzolov/mcp-everything-server:v2")
            .withExposedPorts(3001)
            .withCommand("node","dist/index.js");

    @Test
    @SneakyThrows
    void testHTTP() {
        final var objectMapper = JsonUtils.createMapper();
        final var composingMCPToolBox = ComposingMCPToolBox.buildEmpty()
                .name("Test Composing MCP")
                .objectMapper(objectMapper)
                .build();
        assertNotNull(composingMCPToolBox);
        assertEquals("Test Composing MCP", composingMCPToolBox.name());
        assertTrue(composingMCPToolBox.tools().isEmpty());
        final var payload = Files.readString(Path.of(Objects.requireNonNull(getClass().getResource("/mcp-http.json"))
                                                             .getPath()))
                .formatted(container.getMappedPort(3001));
        MCPJsonReader.loadServers(objectMapper.readValue(payload, MCPConfiguration.class), composingMCPToolBox);
        assertFalse(composingMCPToolBox.tools().isEmpty());
        assertTrue(composingMCPToolBox.tools().size() > 1);
        composingMCPToolBox.exposeTools("test_mcp", "echo");
        assertEquals(1, composingMCPToolBox.tools().size());
        composingMCPToolBox.exposeAllTools("test_mcp");
        assertTrue(composingMCPToolBox.tools().size() > 1);
    }
}
