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
 * Tests {@link ComposingMCPToolBox}
 */
@Testcontainers
class ComposingMCPToolBoxTest {

    @Container
    static GenericContainer<?> container = new GenericContainer<>("tzolov/mcp-everything-server:v2")
            .withExposedPorts(3001)
            .withCommand("node","dist/index.js","sse");

    @Test
    void testBasicCreation() {
        final var objectMapper = JsonUtils.createMapper();
        final var composingMCPToolBox = ComposingMCPToolBox.buildFromFile()
                .name("Test Composing MCP")
                .objectMapper(objectMapper)
                .mcpJsonFilePath(Objects.requireNonNull(getClass().getResource("/mcp.json")).getPath())
                .build();
        assertNotNull(composingMCPToolBox);

        assertFalse(composingMCPToolBox.tools().isEmpty());
        assertEquals(8, composingMCPToolBox.tools().size());
        composingMCPToolBox.exposeTools("test_mcp", "echo");
        assertEquals(1, composingMCPToolBox.tools().size());
        composingMCPToolBox.exposeAllTools("test_mcp");
        assertEquals(8, composingMCPToolBox.tools().size());
    }

    @Test
    @SneakyThrows
    void testSSE() {
        final var objectMapper = JsonUtils.createMapper();
        final var composingMCPToolBox = ComposingMCPToolBox.buildEmpty()
                .name("Test Composing MCP")
                .objectMapper(objectMapper)
                .build();
        assertNotNull(composingMCPToolBox);
        assertEquals("Test Composing MCP", composingMCPToolBox.name());
        assertTrue(composingMCPToolBox.tools().isEmpty());
        final var payload = Files.readString(Path.of(Objects.requireNonNull(getClass().getResource("/mcp-sse.json"))
                                                             .getPath())).formatted(container.getMappedPort(3001));
        MCPJsonReader.loadServers(objectMapper.readValue(payload, MCPConfiguration.class), composingMCPToolBox);
        assertFalse(composingMCPToolBox.tools().isEmpty());
        assertTrue(composingMCPToolBox.tools().size() > 1);
        composingMCPToolBox.exposeTools("test_mcp", "echo");
        assertEquals(1, composingMCPToolBox.tools().size());
        composingMCPToolBox.exposeAllTools("test_mcp");
        assertTrue(composingMCPToolBox.tools().size() > 1);
    }

}