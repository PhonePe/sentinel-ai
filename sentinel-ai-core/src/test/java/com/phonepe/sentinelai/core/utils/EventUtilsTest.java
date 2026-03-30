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

package com.phonepe.sentinelai.core.utils;

import com.google.common.base.Stopwatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.CompactionCompletedEvent;
import com.phonepe.sentinelai.core.events.CompactionStartedEvent;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EventUtils}
 */
@ExtendWith(MockitoExtension.class)
class EventUtilsTest {

    private static final String AGENT_NAME = "test-agent";
    private static final String RUN_ID = "run-001";
    private static final String SESSION_ID = "session-001";
    private static final String USER_ID = "user-001";

    @Mock
    private EventBus eventBus;

    private AgentSetup agentSetup;
    private ModelRunContext modelRunContext;
    private ModelUsageStats usageStats;

    @Test
    void raiseCompactionCompletedEventSuccessNotifiesCorrectEvent() {
        final var stopwatch = Stopwatch.createStarted();
        final var summary = ExtractedSummary.builder()
                .summary("Compact summary")
                .title("Session Title")
                .keywords(List.of("key1", "key2"))
                .build();

        EventUtils.raiseCompactionCompletedEvent(modelRunContext,
                                                 SESSION_ID,
                                                 ErrorType.SUCCESS,
                                                 null,
                                                 usageStats,
                                                 summary,
                                                 stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(CompactionCompletedEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(SESSION_ID, event.getSessionId());
        assertEquals(USER_ID, event.getUserId());
        assertEquals(ErrorType.SUCCESS, event.getErrorType());
        assertEquals(summary, event.getExtractedSummary());
        assertEquals(usageStats, event.getUsageStats());
    }

    @Test
    void raiseCompactionCompletedEventSwallowsException() {
        doThrow(new RuntimeException("bus failure")).when(eventBus).notify(any());

        EventUtils.raiseCompactionCompletedEvent(modelRunContext,
                                                 SESSION_ID,
                                                 ErrorType.SUCCESS,
                                                 null,
                                                 usageStats,
                                                 null,
                                                 Stopwatch.createStarted());

        verify(eventBus).notify(any());
    }

    @Test
    void raiseCompactionCompletedEventWithErrorNotifiesCorrectEvent() {
        final var stopwatch = Stopwatch.createStarted();

        EventUtils.raiseCompactionCompletedEvent(modelRunContext,
                                                 SESSION_ID,
                                                 ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                 "Something went wrong",
                                                 usageStats,
                                                 null,
                                                 stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(CompactionCompletedEvent.class, captor.getValue());
        assertEquals(ErrorType.GENERIC_MODEL_CALL_FAILURE, event.getErrorType());
        assertEquals("Something went wrong", event.getErrorMessage());
    }

    @Test
    void raiseCompactionStartedEventNotifiesCorrectEvent() {
        EventUtils.raiseCompactionStartedEvent(modelRunContext, SESSION_ID);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(CompactionStartedEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(SESSION_ID, event.getSessionId());
        assertEquals(USER_ID, event.getUserId());
    }

    @Test
    void raiseCompactionStartedEventSwallowsException() {
        doThrow(new RuntimeException("bus failure")).when(eventBus).notify(any());

        EventUtils.raiseCompactionStartedEvent(modelRunContext, SESSION_ID);

        verify(eventBus).notify(any());
    }

    @Test
    void raiseInputReceivedEventNotifiesCorrectEvent() {
        final var runContext = new AgentRunContext<>("run-input-1",
                                                     "Hello agent",
                                                     AgentRequestMetadata.builder()
                                                             .sessionId(SESSION_ID)
                                                             .userId(USER_ID)
                                                             .build(),
                                                     agentSetup,
                                                     List.of(),
                                                     usageStats,
                                                     null);

        EventUtils.raiseInputReceivedEvent(AGENT_NAME, runContext, "Hello agent", agentSetup);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(InputReceivedAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals("run-input-1", event.getRunId());
        assertNotNull(event.getContent());
    }

    @Test
    void raiseInputReceivedEventNullMetadataSessionIdIsNull() {
        final var runContext = new AgentRunContext<>("run-input-2",
                                                     "Hello agent",
                                                     null,
                                                     agentSetup,
                                                     List.of(),
                                                     usageStats,
                                                     null);

        EventUtils.raiseInputReceivedEvent(AGENT_NAME, runContext, "Hello agent", agentSetup);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(InputReceivedAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
    }

    @Test
    void raiseInputReceivedEventSwallowsSerializationException() {
        final var runContext = new AgentRunContext<>("run-input-3",
                                                     "hello",
                                                     AgentRequestMetadata.builder()
                                                             .sessionId(SESSION_ID)
                                                             .build(),
                                                     agentSetup,
                                                     List.of(),
                                                     usageStats,
                                                     null);

        doThrow(new RuntimeException("serialize failure")).when(eventBus).notify(any());

        try {
            EventUtils.raiseInputReceivedEvent(AGENT_NAME, runContext, "hello", agentSetup);
        }
        catch (Exception e) {
            fail("Exception should have been swallowed, but was thrown: " + e.getMessage());
        }
    }

    @Test
    void raiseMessageReceivedEventWithExplicitArgsNotifiesCorrectEvent() {
        final var stopwatch = Stopwatch.createStarted();
        final var msg = new UserPrompt(SESSION_ID, RUN_ID, "hello", LocalDateTime.now());

        EventUtils.raiseMessageReceivedEvent(AGENT_NAME,
                                             RUN_ID,
                                             SESSION_ID,
                                             USER_ID,
                                             agentSetup,
                                             List.<AgentMessage>of(msg),
                                             List.<AgentMessage>of(msg),
                                             stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(MessageReceivedAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(1, event.getNewMessages().size());
    }

    @Test
    void raiseMessageReceivedEventWithModelRunContextNotifiesCorrectEvent() {
        final var stopwatch = Stopwatch.createStarted();
        final var msg1 = new UserPrompt(SESSION_ID, RUN_ID, "msg1", LocalDateTime.now());
        final var msg2 = new UserPrompt(SESSION_ID, RUN_ID, "msg2", LocalDateTime.now());
        final List<AgentMessage> allMessages = List.of(msg1, msg2);
        final List<AgentMessage> newMessages = List.of(msg2);

        EventUtils.raiseMessageReceivedEvent(modelRunContext, newMessages, allMessages, stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(MessageReceivedAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(SESSION_ID, event.getSessionId());
        assertEquals(USER_ID, event.getUserId());
        assertEquals(2, event.getAllMessages().size());
        assertEquals(1, event.getNewMessages().size());
        assertNotNull(event.getElapsedTime());
    }

    @Test
    void raiseMessageSentEventAllMessagesAreNewRaisesEventWithAll() {
        final var msg1 = new UserPrompt(SESSION_ID, RUN_ID, "first", LocalDateTime.now());
        final var msg2 = new UserPrompt(SESSION_ID, RUN_ID, "second", LocalDateTime.now());
        final List<AgentMessage> prevMessages = List.of();
        final List<AgentMessage> currentAllMessages = List.of(msg1, msg2);

        EventUtils.raiseMessageSentEvent(modelRunContext, prevMessages, currentAllMessages);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(MessageSentAgentEvent.class, captor.getValue());
        assertEquals(2, event.getNewMessages().size());
    }

    @Test
    void raiseMessageSentEventFiltersOutNonRequestMessages() {
        // Text is a model response, not an AgentRequest — it must be excluded from sent-message events
        final var textMsg = new Text(SESSION_ID, RUN_ID, "llm response", usageStats, 100L);
        final List<AgentMessage> currentAllMessages = List.of(textMsg);

        EventUtils.raiseMessageSentEvent(modelRunContext, List.of(), currentAllMessages);

        verify(eventBus, never()).notify(any());
    }

    @Test
    void raiseMessageSentEventNoNewMessagesDoesNotNotify() {
        final var msg1 = new UserPrompt(SESSION_ID, RUN_ID, "old message", LocalDateTime.now());

        EventUtils.raiseMessageSentEvent(modelRunContext, List.of(msg1), List.of(msg1));

        verify(eventBus, never()).notify(any());
    }

    @Test
    void raiseMessageSentEventSendsOnlyNewRequestMessages() {
        final var msg1 = new UserPrompt(SESSION_ID, RUN_ID, null, null, "old message", false, LocalDateTime.now());
        final var msg2 = new UserPrompt(SESSION_ID, RUN_ID, null, null, "new message", false, LocalDateTime.now());

        EventUtils.raiseMessageSentEvent(modelRunContext, List.of(msg1), List.of(msg1, msg2));

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(MessageSentAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(1, event.getNewMessages().size());
        assertEquals("new message", ((UserPrompt) event.getNewMessages().get(0)).getContent());
    }

    @Test
    void raiseOutputEventErrorRaisesOutputErrorEvent() {
        final var stopwatch = Stopwatch.createStarted();
        final var error = SentinelError.error(ErrorType.GENERIC_MODEL_CALL_FAILURE, "LLM error");
        final var output = ModelOutput.error(List.of(), usageStats, error);

        EventUtils.raiseOutputEvent(modelRunContext, output, stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(OutputErrorAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(ErrorType.GENERIC_MODEL_CALL_FAILURE, event.getErrorType());
        assertNotNull(event.getElapsedTime());
    }

    @Test
    void raiseOutputEventSuccessRaisesOutputGeneratedEvent() throws Exception {
        final var stopwatch = Stopwatch.createStarted();
        final var mapper = JsonUtils.createMapper();
        final var data = mapper.readTree("{\"result\":\"ok\"}");
        final var output = ModelOutput.success(data, List.of(), List.of(), usageStats);

        EventUtils.raiseOutputEvent(modelRunContext, output, stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        final var event = assertInstanceOf(OutputGeneratedAgentEvent.class, captor.getValue());
        assertEquals(AGENT_NAME, event.getAgentName());
        assertEquals(RUN_ID, event.getRunId());
        assertEquals(SESSION_ID, event.getSessionId());
        assertEquals(USER_ID, event.getUserId());
        assertNotNull(event.getContent());
        assertNotNull(event.getElapsedTime());
    }

    @Test
    void raiseOutputEventSuccessWithNullErrorRaisesOutputGeneratedEvent() throws Exception {
        final var stopwatch = Stopwatch.createStarted();
        final var mapper = JsonUtils.createMapper();
        final var data = mapper.readTree("{\"value\":42}");
        // null error field is treated as the success branch in raiseOutputEvent
        final var output = new ModelOutput(data, List.of(), List.of(), usageStats, null);

        EventUtils.raiseOutputEvent(modelRunContext, output, stopwatch);

        final var captor = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventBus).notify(captor.capture());

        assertInstanceOf(OutputGeneratedAgentEvent.class, captor.getValue());
    }

    @Test
    void raiseOutputEventSwallowsException() throws Exception {
        final var stopwatch = Stopwatch.createStarted();
        final var mapper = JsonUtils.createMapper();
        final var data = mapper.readTree("{\"result\":\"ok\"}");
        final var output = ModelOutput.success(data, List.of(), List.of(), usageStats);

        doThrow(new RuntimeException("event bus failure")).when(eventBus).notify(any());

        EventUtils.raiseOutputEvent(modelRunContext, output, stopwatch);

        verify(eventBus).notify(any());
    }

    @BeforeEach
    void setUp() {
        agentSetup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .eventBus(eventBus)
                .build();

        usageStats = new ModelUsageStats();

        modelRunContext = new ModelRunContext(AGENT_NAME,
                                              RUN_ID,
                                              SESSION_ID,
                                              USER_ID,
                                              agentSetup,
                                              usageStats,
                                              null);
    }
}
