package com.phonepe.sentinelai.toolbox.mcp;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link ComposingMCPToolBox}
 */
class ComposingMCPToolBoxStdIOTest {

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
        final var originalSize = composingMCPToolBox.tools().size();
        assertTrue(originalSize >= 8);
        composingMCPToolBox.exposeTools("test_mcp", "echo");
        assertEquals(1, composingMCPToolBox.tools().size());
        composingMCPToolBox.exposeAllTools("test_mcp");
        assertEquals(originalSize, composingMCPToolBox.tools().size());
    }

}