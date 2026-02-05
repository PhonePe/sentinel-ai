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

package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.Getter;
import lombok.experimental.UtilityClass;

/**
 * Types of MCP servers supported
 */
@Getter
public enum MCPServerType {

    STDIO(Values.STDIO_TEXT), SSE(Values.SSE_TEXT), HTTP(Values.HTTP_TEXT);

    @UtilityClass
    public static final class Values {
        public static final String STDIO_TEXT = "stdio";
        public static final String SSE_TEXT = "sse";
        public static final String HTTP_TEXT = "http";
    }

    private final String value;

    MCPServerType(String value) {
        this.value = value;
    }
}
