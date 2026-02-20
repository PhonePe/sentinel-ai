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

package com.phonepe.sentinelai.session.history.modifiers;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptRemovalPreFilterTest {

    @Test
    void testFilterEmptyListReturnsEmpty() {
        var filter = new SystemPromptRemovalPreFilter();
        var messages = new ArrayList<AgentMessage>();
        var result = filter.filter(messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterKeepsNonSystemPromptMessages() {
        var filter = new SystemPromptRemovalPreFilter();
        var sessionId = "session-2";
        var runId = "run-2";
        var messages = List.of(
                               new UserPrompt(sessionId, runId, "user message 1", LocalDateTime.now()),
                               new Text(sessionId, runId, "response 1", new ModelUsageStats(), 100),
                               new UserPrompt(sessionId, runId, "user message 2", LocalDateTime.now()),
                               new Text(sessionId, runId, "response 2", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(4, result.size());
    }

    @Test
    void testFilterPreservesMessageOrder() {
        var filter = new SystemPromptRemovalPreFilter();
        var sessionId = "session-4";
        var runId = "run-4";
        var userPrompt = new UserPrompt(sessionId, runId, "user message", LocalDateTime.now());
        var textResponse = new Text(sessionId, runId, "response", new ModelUsageStats(), 100);
        var messages = List.of(
                               new SystemPrompt(sessionId, runId, "system prompt", true, "method1"),
                               userPrompt,
                               new SystemPrompt(sessionId, runId, "another system prompt", false, "method2"),
                               textResponse
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertEquals(userPrompt, result.get(0));
        assertEquals(textResponse, result.get(1));
    }

    @Test
    void testFilterRemovesAllSystemPrompts() {
        var filter = new SystemPromptRemovalPreFilter();
        var sessionId = "session-1";
        var runId = "run-1";
        var messages = List.of(
                               new SystemPrompt(sessionId, runId, "system prompt 1", true, "method1"),
                               new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                               new SystemPrompt(sessionId, runId, "system prompt 2", false, "method2"),
                               new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m.getMessageType().name().contains("SYSTEM_PROMPT")));
    }

    @Test
    void testFilterWithDynamicAndStaticPrompts() {
        var filter = new SystemPromptRemovalPreFilter();
        var sessionId = "session-5";
        var runId = "run-5";
        var messages = List.<AgentMessage>of(
                                             new SystemPrompt(sessionId, runId, "static prompt", false, null),
                                             new SystemPrompt(sessionId,
                                                              runId,
                                                              "dynamic prompt",
                                                              true,
                                                              "generatePrompt"),
                                             new UserPrompt(sessionId, runId, "user input", LocalDateTime.now())
        );
        var result = filter.filter(messages);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof UserPrompt);
    }

    @Test
    void testFilterWithMultipleSessionsAndRuns() {
        var filter = new SystemPromptRemovalPreFilter();
        var messages = List.<AgentMessage>of(
                                             new SystemPrompt("s1", "r1", "sys1", true, "m1"),
                                             new UserPrompt("s1", "r1", "u1", LocalDateTime.now()),
                                             new SystemPrompt("s2", "r2", "sys2", false, "m2"),
                                             new UserPrompt("s2", "r2", "u2", LocalDateTime.now()),
                                             new SystemPrompt("s3", "r3", "sys3", true, "m3")
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m instanceof UserPrompt));
    }

    @Test
    void testFilterWithOnlySystemPromptsReturnsEmpty() {
        var filter = new SystemPromptRemovalPreFilter();
        var sessionId = "session-3";
        var runId = "run-3";
        var messages = List.<AgentMessage>of(
                                             new SystemPrompt(sessionId, runId, "system prompt 1", true, "method1"),
                                             new SystemPrompt(sessionId, runId, "system prompt 2", false, "method2"),
                                             new SystemPrompt(sessionId, runId, "system prompt 3", true, "method3")
        );
        var result = filter.filter(messages);
        assertTrue(result.isEmpty());
    }
}
