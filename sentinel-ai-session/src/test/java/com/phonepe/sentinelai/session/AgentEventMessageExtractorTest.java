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

package com.phonepe.sentinelai.session;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEventMessageExtractorTest {

    @Test
    void testExtractedDataContainsCorrectMessageTypes() {
        var extractor = new AgentEventMessageExtractor();
        var sessionId = "session-12";
        var runId = "run-12";
        var userPrompt = new UserPrompt(sessionId, runId, "user message", LocalDateTime.now());
        var textResponse = new Text(sessionId, runId, "response", new ModelUsageStats(), 100);
        var newMessages = List.<AgentMessage>of(userPrompt);
        var allMessages = List.<AgentMessage>of(userPrompt, textResponse);
        var event = MessageReceivedAgentEvent.builder()
                .agentName("test-agent")
                .runId(runId)
                .sessionId(sessionId)
                .userId("user-1")
                .newMessages(newMessages)
                .allMessages(allMessages)
                .elapsedTime(Duration.ofMillis(100))
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertTrue(extractedData.getNewMessages().getFirst() instanceof UserPrompt);
        assertTrue(extractedData.getAllMessages().get(1) instanceof Text);
    }

    @Test
    void testMessageReceivedEventWithEmptyMessages() {
        var extractor = new AgentEventMessageExtractor();
        var event = MessageReceivedAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-9")
                .sessionId("session-9")
                .userId("user-1")
                .newMessages(List.of())
                .allMessages(List.of())
                .elapsedTime(Duration.ZERO)
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertTrue(extractedData.getNewMessages().isEmpty());
        assertTrue(extractedData.getAllMessages().isEmpty());
    }

    @Test
    void testMessageReceivedEventWithMultipleNewMessages() {
        var extractor = new AgentEventMessageExtractor();
        var sessionId = "session-11";
        var runId = "run-11";
        var newMessages = List.<AgentMessage>of(
                                                new UserPrompt(sessionId, runId, "message 1", LocalDateTime.now()),
                                                new Text(sessionId, runId, "response 1", new ModelUsageStats(), 100),
                                                new UserPrompt(sessionId, runId, "message 2", LocalDateTime.now())
        );
        var allMessages = List.<AgentMessage>of(
                                                new UserPrompt(sessionId, runId, "message 1", LocalDateTime.now()),
                                                new Text(sessionId, runId, "response 1", new ModelUsageStats(), 100),
                                                new UserPrompt(sessionId, runId, "message 2", LocalDateTime.now())
        );
        var event = MessageReceivedAgentEvent.builder()
                .agentName("test-agent")
                .runId(runId)
                .sessionId(sessionId)
                .userId("user-1")
                .newMessages(newMessages)
                .allMessages(allMessages)
                .elapsedTime(Duration.ofMillis(300))
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertEquals(3, extractedData.getNewMessages().size());
        assertEquals(3, extractedData.getAllMessages().size());
    }

    @Test
    void testMessageSentEventWithEmptyMessages() {
        var extractor = new AgentEventMessageExtractor();
        var event = MessageSentAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-10")
                .sessionId("session-10")
                .userId("user-1")
                .newMessages(List.of())
                .allMessages(List.of())
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertTrue(extractedData.getNewMessages().isEmpty());
        assertTrue(extractedData.getAllMessages().isEmpty());
    }

    @Test
    void testVisitInputReceivedEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = InputReceivedAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .content("input content")
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVisitMessageReceivedEventExtractsMessages() {
        var extractor = new AgentEventMessageExtractor();
        var sessionId = "session-2";
        var runId = "run-2";
        var newMessages = List.<AgentMessage>of(
                                                new UserPrompt(sessionId, runId, "user message", LocalDateTime.now())
        );
        var allMessages = List.<AgentMessage>of(
                                                new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                                new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var event = MessageReceivedAgentEvent.builder()
                .agentName("test-agent")
                .runId(runId)
                .sessionId(sessionId)
                .userId("user-1")
                .newMessages(newMessages)
                .allMessages(allMessages)
                .elapsedTime(Duration.ofMillis(100))
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertEquals(1, extractedData.getNewMessages().size());
        assertEquals(2, extractedData.getAllMessages().size());
    }

    @Test
    void testVisitMessageSentEventExtractsMessages() {
        var extractor = new AgentEventMessageExtractor();
        var sessionId = "session-3";
        var runId = "run-3";
        var newMessages = List.<AgentMessage>of(
                                                new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var allMessages = List.<AgentMessage>of(
                                                new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                                new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var event = MessageSentAgentEvent.builder()
                .agentName("test-agent")
                .runId(runId)
                .sessionId(sessionId)
                .userId("user-1")
                .newMessages(newMessages)
                .allMessages(allMessages)
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isPresent());
        var extractedData = result.get();
        assertEquals(1, extractedData.getNewMessages().size());
        assertEquals(2, extractedData.getAllMessages().size());
    }

    @Test
    void testVisitOutputErrorEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = OutputErrorAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-4")
                .sessionId("session-4")
                .userId("user-1")
                .errorType(ErrorType.GENERIC_MODEL_CALL_FAILURE)
                .content("error content")
                .elapsedTime(Duration.ofMillis(50))
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVisitOutputGeneratedEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = OutputGeneratedAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-5")
                .sessionId("session-5")
                .userId("user-1")
                .content("output content")
                .usage(new ModelUsageStats())
                .elapsedTime(Duration.ofMillis(200))
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVisitToolCallApprovalDeniedEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = ToolCallApprovalDeniedAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-6")
                .sessionId("session-6")
                .userId("user-1")
                .toolCallId("tc-1")
                .toolCallName("dangerousTool")
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVisitToolCallCompletedEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = new ToolCallCompletedAgentEvent(
                                                    "test-agent",
                                                    "run-7",
                                                    "session-7",
                                                    "user-1",
                                                    "tc-1",
                                                    "someTool",
                                                    ErrorType.SUCCESS,
                                                    "result",
                                                    Duration.ofMillis(100)
        );
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }

    @Test
    void testVisitToolCalledEventReturnsEmpty() {
        var extractor = new AgentEventMessageExtractor();
        var event = ToolCalledAgentEvent.builder()
                .agentName("test-agent")
                .runId("run-8")
                .sessionId("session-8")
                .userId("user-1")
                .toolCallId("tc-1")
                .toolCallName("someTool")
                .arguments("{\"arg\": \"value\"}")
                .build();
        var result = extractor.visit(event);
        assertTrue(result.isEmpty());
    }
}
