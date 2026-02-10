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

package com.phonepe.sentinelai.session.history.selectors;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullRunMessageSelectorTest {

    @Test
    void testSelectEmptyInputReturnsEmptyList() {
        var selector = new FullRunMessageSelector();
        final var sessionId = "session-empty";
        final var messages = new ArrayList<AgentMessage>();
        final var result = selector.select(sessionId, messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectMixedRunsKeepsAllMessagesForFullRuns() {
        var selector = new FullRunMessageSelector();
        final var runA = "run-A";
        final var runB = "run-B";
        final var runC = "run-C";
        final var sessionId = "session-3";
        final var messages = List.of(new UserPrompt(sessionId,
                                                    runA,
                                                    "u1",
                                                    LocalDateTime.now()),
                                     new UserPrompt(sessionId,
                                                    runB,
                                                    "u2",
                                                    LocalDateTime.now()),
                                     new Text(sessionId,
                                              runA,
                                              "t1",
                                              new ModelUsageStats(),
                                              100),
                                     new StructuredOutput(sessionId,
                                                          runB,
                                                          "{}",
                                                          new ModelUsageStats(),
                                                          100),
                                     new Text(sessionId,
                                              runC,
                                              "t-only",
                                              new ModelUsageStats(),
                                              100));
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = selector.select(sessionId, modifiable);
        assertEquals(4, result.size());
        assertFalse(result.stream().anyMatch(m -> runC.equals(m.getRunId())));
        assertTrue(result.stream().anyMatch(m -> runA.equals(m.getRunId())));
        assertTrue(result.stream().anyMatch(m -> runB.equals(m.getRunId())));
    }

    @Test
    void testSelectWithStructuredOutputRemovesIncompleteRuns() {
        var selector = new FullRunMessageSelector();
        final var runComplete = "run-so-complete";
        final var runIncomplete = "run-so-incomplete";
        final var sessionId = "session-2";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId,
                                    runComplete,
                                    "ask",
                                    LocalDateTime.now()));
        messages.add(new StructuredOutput(sessionId,
                                          runComplete,
                                          "{}",
                                          new ModelUsageStats(),
                                          100));
        messages.add(new StructuredOutput(sessionId,
                                          runIncomplete,
                                          "{}",
                                          new ModelUsageStats(),
                                          100));
        final var result = selector.select(sessionId, messages);
        assertEquals(2, result.size());
        assertTrue(result.stream()
                .allMatch(m -> runComplete.equals(m.getRunId())));
    }

    @Test
    void testSelectWithTextResponseRemovesIncompleteRuns() {
        var selector = new FullRunMessageSelector();
        final var runComplete = "run-complete";
        final var runIncomplete = "run-incomplete";
        final var sessionId = "session-1";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId,
                                    runComplete,
                                    "hello",
                                    LocalDateTime.now()));
        messages.add(new Text(sessionId,
                              runComplete,
                              "hi",
                              new ModelUsageStats(),
                              100));
        messages.add(new UserPrompt(sessionId,
                                    runIncomplete,
                                    "only user",
                                    LocalDateTime.now()));
        final var result = selector.select(sessionId, messages);
        assertEquals(2, result.size());
        assertTrue(result.stream()
                .allMatch(m -> runComplete.equals(m.getRunId())));
    }
}
