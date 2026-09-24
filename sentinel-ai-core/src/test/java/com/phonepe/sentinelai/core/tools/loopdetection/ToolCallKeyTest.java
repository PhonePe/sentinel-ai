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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ToolCallKeyTest {

    static Stream<Arguments> keyEqualityScenarios() {
        return Stream.of(
                         Arguments.of("different arguments produce different keys",
                                      "search",
                                      "{\"a\":1}",
                                      "search",
                                      "{\"a\":2}",
                                      false),
                         Arguments.of("different tool names produce different keys",
                                      "search",
                                      "{\"a\":1}",
                                      "fetch",
                                      "{\"a\":1}",
                                      false),
                         Arguments.of("key order and whitespace do not change the key",
                                      "search",
                                      "{\"b\":2,\"a\":1}",
                                      "search",
                                      "{ \"a\" : 1, \"b\" : 2 }",
                                      true),
                         Arguments.of("nested object key order is normalized",
                                      "search",
                                      "{\"x\":{\"z\":1,\"y\":2}}",
                                      "search",
                                      "{\"x\":{\"y\":2,\"z\":1}}",
                                      true),
                         Arguments.of("array order is preserved",
                                      "search",
                                      "[1,2]",
                                      "search",
                                      "[2,1]",
                                      false),
                         Arguments.of("non-JSON arguments are used as is",
                                      "search",
                                      "not-json",
                                      "search",
                                      "not-json",
                                      true),
                         Arguments.of("null arguments are treated as empty",
                                      "search",
                                      null,
                                      "search",
                                      "",
                                      true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyEqualityScenarios")
    void keyEquality(String name,
                     String toolName,
                     String arguments,
                     String otherToolName,
                     String otherArguments,
                     boolean expectEqual) {
        final var mapper = JsonUtils.createMapper();
        final var key1 = new ToolCallKey(toolName, arguments, mapper);
        final var key2 = new ToolCallKey(otherToolName, otherArguments, mapper);

        if (expectEqual) {
            assertEquals(key1.asString(), key2.asString());
        }
        else {
            assertNotEquals(key1.asString(), key2.asString());
        }
    }
}
