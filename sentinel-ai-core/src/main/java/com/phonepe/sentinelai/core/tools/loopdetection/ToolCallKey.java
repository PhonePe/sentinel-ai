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

package com.phonepe.sentinelai.core.tools.loopdetection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.utils.AgentUtils;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.TreeMap;

/**
 * A stable, canonical key for a tool call. The key is built from the tool name and a
 * canonical form of the arguments. The canonical form serializes JSON objects with
 * sorted keys, so two argument strings that differ only in key order or
 * whitespace produce the same key. If the arguments are not valid JSON, the raw string is
 * used as-is.
 */
@Value
@Slf4j
public class ToolCallKey {

    private static final SortingNodeFactory SORTING_NODE_FACTORY = new SortingNodeFactory();

    private static class SortingNodeFactory extends JsonNodeFactory {
        @Override
        public ObjectNode objectNode() {
            return new ObjectNode(this, new TreeMap<String, JsonNode>());
        }
    }

    String toolName;

    String canonicalArguments;

    public ToolCallKey(@NonNull String toolName, String arguments, ObjectMapper mapper) {
        this.toolName = toolName;
        this.canonicalArguments = canonicalize(arguments, mapper);
    }

    private static String canonicalize(String arguments, ObjectMapper mapper) {
        final var raw = null == arguments ? "" : arguments;
        if (Strings.isNullOrEmpty(raw)) {
            return "";
        }
        try {
            final var node = mapper.reader(SORTING_NODE_FACTORY)
                    .readTree(raw);
            return mapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(node);
        }
        catch (Exception e) {
            log.warn("Error creating sorted json: {}. Input: {}",
                     AgentUtils.rootCause(e).getMessage(),
                     raw);
            return raw;
        }
    }

    public String asString() {
        return toolName + "\n" + canonicalArguments;
    }
}
