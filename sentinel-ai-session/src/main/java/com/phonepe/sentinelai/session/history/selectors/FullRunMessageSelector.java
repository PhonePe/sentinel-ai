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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Returns messages for full runs only. Removes messages that have come from incomplete runs.
 */
public class FullRunMessageSelector implements MessageSelector {
    private static final Set<AgentMessageType> TEXT_REQ_RES = Set.of(
                                                                     AgentMessageType.USER_PROMPT_REQUEST_MESSAGE,
                                                                     AgentMessageType.TEXT_RESPONSE_MESSAGE);
    private static final Set<AgentMessageType> SO_REQ_RES = Set.of(
                                                                   AgentMessageType.USER_PROMPT_REQUEST_MESSAGE,
                                                                   AgentMessageType.STRUCTURED_OUTPUT_RESPONSE_MESSAGE);

    @Override
    public List<AgentMessage> select(String sessionId,
                                     List<AgentMessage> messages) {

        final var runMessages = messages.stream()
                .collect(groupingBy(AgentMessage::getRunId,
                                    mapping(AgentMessage::getMessageType,
                                            toUnmodifiableSet())))
                .entrySet()
                .stream()
                .filter(e -> e.getValue().containsAll(TEXT_REQ_RES) || e
                        .getValue()
                        .containsAll(SO_REQ_RES))
                .map(Map.Entry::getKey)
                .collect(toUnmodifiableSet());
        final var candidates = new ArrayList<>(messages);
        candidates.removeIf(m -> !runMessages.contains(m.getRunId()));
        return candidates;
    }
}
