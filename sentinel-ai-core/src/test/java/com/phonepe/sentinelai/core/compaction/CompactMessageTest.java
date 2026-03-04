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

package com.phonepe.sentinelai.core.compaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompactMessageTest {

    @Test
    void testRolesClassExists() {
        assertNotNull(CompactMessage.Roles.class);
    }

    @Test
    void testRolesConstants() {
        assertEquals("system", CompactMessage.Roles.SYSTEM);
        assertEquals("user", CompactMessage.Roles.USER);
        assertEquals("assistant", CompactMessage.Roles.ASSISTANT);
    }

    @Test
    void testTypesClassExists() {
        assertNotNull(CompactMessage.Types.class);
    }

    @Test
    void testTypesConstants() {
        assertEquals("chat", CompactMessage.Types.CHAT);
        assertEquals("tool_call", CompactMessage.Types.TOOL_CALL);
        assertEquals("tool_call_response", CompactMessage.Types.TOOL_CALL_RESPONSE);
    }
}
