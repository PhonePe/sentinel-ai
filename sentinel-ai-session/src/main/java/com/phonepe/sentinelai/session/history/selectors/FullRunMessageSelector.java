package com.phonepe.sentinelai.session.history.selectors;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.*;

/**
 * Returns messages for full runs only. Removes messages that have come from incomplete runs.
 */
public class FullRunMessageSelector implements MessageSelector {
    private static final Set<AgentMessageType> TEXT_REQ_RES = Set.of(
            AgentMessageType.USER_PROMPT_REQUEST_MESSAGE, AgentMessageType.TEXT_RESPONSE_MESSAGE);
    private static final Set<AgentMessageType> SO_REQ_RES = Set.of(
            AgentMessageType.USER_PROMPT_REQUEST_MESSAGE, AgentMessageType.STRUCTURED_OUTPUT_RESPONSE_MESSAGE);

    @Override
    public List<AgentMessage> select(String sessionId, List<AgentMessage> messages) {

        final var runMessages = messages.stream()
                .collect(groupingBy(AgentMessage::getRunId,
                                    mapping(AgentMessage::getMessageType, toUnmodifiableSet())))
                .entrySet()
                .stream()
                .filter(e -> e.getValue().containsAll(TEXT_REQ_RES)
                                                     || e.getValue().containsAll(SO_REQ_RES))
                .map(Map.Entry::getKey)
                .collect(toUnmodifiableSet());
        final var candidates = new ArrayList<>(messages);
        candidates.removeIf(m -> !runMessages.contains(m.getRunId()));
        return candidates;
    }
}
