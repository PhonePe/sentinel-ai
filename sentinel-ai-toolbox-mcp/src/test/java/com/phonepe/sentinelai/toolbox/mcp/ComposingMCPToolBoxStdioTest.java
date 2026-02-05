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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ComposingMCPToolBox}
 */
class ComposingMCPToolBoxStdioTest {

    @Test
    void testBasicCreation() {
        final var objectMapper = JsonUtils.createMapper();
        final var composingMCPToolBox = ComposingMCPToolBox.buildFromFile()
                .name("Test Composing MCP")
                .objectMapper(objectMapper)
                .mcpJsonFilePath(Objects.requireNonNull(getClass().getResource(
                                                                               "/mcp.json"))
                        .getPath())
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
