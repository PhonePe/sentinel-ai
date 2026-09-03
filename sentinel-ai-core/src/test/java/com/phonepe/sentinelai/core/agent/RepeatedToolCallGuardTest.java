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

package com.phonepe.sentinelai.core.agent;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedToolCallGuardTest {

    private static final String SESSION_ID = "test-session";
    private static final String RUN_ID = "test-run";

    private static ToolCall toolCall(String toolCallId, String toolName, String arguments) {
        return ToolCall.builder()
                .sessionId(SESSION_ID)
                .runId(RUN_ID)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments(arguments)
                .build();
    }

    @Test
    void allowsCallsBelowThreshold() {
        final var guard = new RepeatedToolCallGuard(3, SESSION_ID, RUN_ID);

        assertNull(guard.registerCall(toolCall("call-1", "bash", "{}")));
        assertNull(guard.registerCall(toolCall("call-2", "bash", "{}")));
    }

    @Test
    void blocksThirdConsecutiveIdenticalCall() {
        final var guard = new RepeatedToolCallGuard(3, SESSION_ID, RUN_ID);
        guard.registerCall(toolCall("call-1", "bash", "{\"command\":\"ls\"}"));
        guard.registerCall(toolCall("call-2", "bash", "{\"command\":\"ls\"}"));

        final var blocked = guard.registerCall(toolCall("call-3", "bash", "{\"command\":\"ls\"}"));

        assertNotNull(blocked);
        assertEquals(ErrorType.TOOL_CALL_PERMANENT_FAILURE, blocked.getErrorType());
        assertEquals("call-3", blocked.getToolCallId());
        assertEquals("bash", blocked.getToolName());
        assertEquals(SESSION_ID, blocked.getSessionId());
        assertEquals(RUN_ID, blocked.getRunId());
        assertTrue(blocked.getResponse().toLowerCase(java.util.Locale.ROOT).contains("do not call"));
        assertTrue(blocked.getResponse().toLowerCase(java.util.Locale.ROOT).contains("previous"));
        assertTrue(blocked.getResponse().contains("'bash'"));
    }

    @Test
    void differentArgumentsResetTheCount() {
        final var guard = new RepeatedToolCallGuard(3, SESSION_ID, RUN_ID);
        guard.registerCall(toolCall("call-1", "bash", "{\"command\":\"ls\"}"));
        guard.registerCall(toolCall("call-2", "bash", "{\"command\":\"ls\"}"));

        // A different call resets the consecutive count
        assertNull(guard.registerCall(toolCall("call-3", "bash", "{\"command\":\"pwd\"}")));
        assertNull(guard.registerCall(toolCall("call-4", "bash", "{\"command\":\"ls\"}")));
        assertNull(guard.registerCall(toolCall("call-5", "bash", "{\"command\":\"ls\"}")));
    }

    @Test
    void differentToolNamesResetTheCount() {
        final var guard = new RepeatedToolCallGuard(3, SESSION_ID, RUN_ID);
        guard.registerCall(toolCall("call-1", "bash", "{}"));
        guard.registerCall(toolCall("call-2", "bash", "{}"));

        assertNull(guard.registerCall(toolCall("call-3", "read_file", "{}")));
        assertNull(guard.registerCall(toolCall("call-4", "bash", "{}")));
        assertNull(guard.registerCall(toolCall("call-5", "bash", "{}")));
    }

    @Test
    void disabledGuardNeverBlocks() {
        final var guard = new RepeatedToolCallGuard(0, SESSION_ID, RUN_ID);

        for (int i = 1; i <= 5; i++) {
            assertNull(guard.registerCall(toolCall("call-" + i, "bash", "{}")));
        }
    }

    @Test
    void keepsBlockingWhileTheRepeatContinues() {
        final var guard = new RepeatedToolCallGuard(2, SESSION_ID, RUN_ID);
        guard.registerCall(toolCall("call-1", "bash", "{}"));

        assertNotNull(guard.registerCall(toolCall("call-2", "bash", "{}")));
        assertNotNull(guard.registerCall(toolCall("call-3", "bash", "{}")));
    }
}
