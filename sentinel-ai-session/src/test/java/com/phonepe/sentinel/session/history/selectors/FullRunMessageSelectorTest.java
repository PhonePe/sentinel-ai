package com.phonepe.sentinel.session.history.selectors;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FullRunMessageSelectorTest {

    @Test
    void testSelectWithTextResponseRemovesIncompleteRuns() {
        var selector = new FullRunMessageSelector();
        final var runComplete = "run-complete";
        final var runIncomplete = "run-incomplete";
        final var sessionId = "session-1";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId, runComplete, "hello", LocalDateTime.now()));
        messages.add(new Text(sessionId, runComplete, "hi"));
        messages.add(new UserPrompt(sessionId, runIncomplete, "only user", LocalDateTime.now()));
        final var result = selector.select(sessionId, messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> runComplete.equals(m.getRunId())));
    }

    @Test
    void testSelectWithStructuredOutputRemovesIncompleteRuns() {
        var selector = new FullRunMessageSelector();
        final var runComplete = "run-so-complete";
        final var runIncomplete = "run-so-incomplete";
        final var sessionId = "session-2";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId, runComplete, "ask", LocalDateTime.now()));
        messages.add(new StructuredOutput(sessionId, runComplete, "{}"));
        messages.add(new StructuredOutput(sessionId, runIncomplete, "{}"));
        final var result = selector.select(sessionId, messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> runComplete.equals(m.getRunId())));
    }

    @Test
    void testSelectMixedRunsKeepsAllMessagesForFullRuns() {
        var selector = new FullRunMessageSelector();
        final var runA = "run-A";
        final var runB = "run-B";
        final var runC = "run-C";
        final var sessionId = "session-3";
        final var messages = List.of(
                new UserPrompt(sessionId, runA, "u1", LocalDateTime.now()),
                new UserPrompt(sessionId, runB, "u2", LocalDateTime.now()),
                new Text(sessionId, runA, "t1"),
                new StructuredOutput(sessionId, runB, "{}"),
                new Text(sessionId, runC, "t-only")
        );
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = selector.select(sessionId, modifiable);
        assertEquals(4, result.size());
        assertFalse(result.stream().anyMatch(m -> runC.equals(m.getRunId())));
        assertTrue(result.stream().anyMatch(m -> runA.equals(m.getRunId())));
        assertTrue(result.stream().anyMatch(m -> runB.equals(m.getRunId())));
    }

    @Test
    void testSelectEmptyInputReturnsEmptyList() {
        var selector = new FullRunMessageSelector();
        final var sessionId = "session-empty";
        final var messages = new ArrayList<AgentMessage>();
        final var result = selector.select(sessionId, messages);
        assertTrue(result.isEmpty());
    }
}