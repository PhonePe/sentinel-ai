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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    boolean deleteSession(String sessionId);

    /**
     * Reads messages for a specific session with pagination support.
     * This method helps clients keep their state management simpler. Basically the same
     * {@link com.phonepe.sentinelai.session.BiScrollable.DataPointer} can be passed back to the server to get the
     * next set of messages in both directions. Messages are always returned in chronological order (oldest to newest).
     *
     * @param sessionId        The unique identifier for the session.
     * @param count            The maximum number of messages to retrieve.
     * @param skipSystemPrompt If true, system prompt request messages will be excluded from the result.
     * @param pointer          The {@link com.phonepe.sentinelai.session.BiScrollable.DataPointer} used by the client
     *                         to indicate the current position in the message list.
     * @param queryDirection   The direction to scroll in: {@link QueryDirection#OLDER} to fetch messages before the
     *                         pointer,
     *                         or {@link QueryDirection#NEWER} to fetch messages after the pointer.
     * @return A {@link BiScrollable} containing the list of messages (sorted chronologically) and pointers for
     *         further scrolling.
     */
    BiScrollable<AgentMessage> readMessages(String sessionId,
                                            int count,
                                            boolean skipSystemPrompt,
                                            BiScrollable.DataPointer pointer,
                                            QueryDirection queryDirection);

    void saveMessages(String sessionId,
                      String runId,
                      List<AgentMessage> messages);

    Optional<SessionSummary> saveSession(SessionSummary sessionSummary);

    Optional<SessionSummary> session(String sessionId);

    BiScrollable<SessionSummary> sessions(int count,
                                          String pointer,
                                          QueryDirection queryDirection);

}
