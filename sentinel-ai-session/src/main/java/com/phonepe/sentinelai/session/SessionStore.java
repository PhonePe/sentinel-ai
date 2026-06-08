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
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * A storage system for agent session
 */
public abstract class SessionStore {

    private final SessionExtraDataOperator extraDataOperator;

    protected SessionStore(@Nullable SessionExtraDataOperator extraDataOperator) {
        this.extraDataOperator = Objects.requireNonNullElse(extraDataOperator, SessionExtraDataOperator.empty());
    }

    /**
     * Deletes a session and all its associated data.
     *
     * @param sessionId The unique identifier for the session to delete.
     * @return true if the session was successfully deleted, false otherwise.
     */
    public abstract boolean deleteSession(String sessionId);

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
    public abstract BiScrollable<AgentMessage> readMessages(String sessionId,
                                                            int count,
                                                            boolean skipSystemPrompt,
                                                            BiScrollable.DataPointer pointer,
                                                            QueryDirection queryDirection);

    /**
     * Saves a list of messages for a specific session and run. The session will be created if it doesn't exist.
     *
     * @param sessionId The unique identifier for the session.
     * @param runId     The unique identifier for the run within the session.
     * @param messages  The list of messages to save, in chronological order (oldest to newest).
     */
    public abstract void saveMessages(String sessionId,
                                      String runId,
                                      List<AgentMessage> messages);

    /**
     * Saves the session summary.
     * The session summary will be created if it doesn't exist.
     *
     * @param sessionSummary The session summary to save.
     * @return The saved session summary, or empty if the save operation failed.
     */
    public final Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
        return saveSessionImpl(extraDataOperator.apply(sessionSummary));
    }

    /**
     * Retrieves the session summary for a specific session.
     *
     * @param sessionId The unique identifier for the session.
     * @return An Optional containing the session summary if found, or empty if not found.
     */
    public abstract Optional<SessionSummary> session(String sessionId);

    /**
     * Lists session summaries with pagination support. The sessions are returned in reverse chronological order
     * (newest to oldest).
     *
     * @param count          The maximum number of session summaries to retrieve.
     * @param pointer        The {@link com.phonepe.sentinelai.session.BiScrollable.DataPointer} used by the client
     *                       to indicate the current position in the session list.
     * @param queryDirection The direction to scroll in: {@link QueryDirection#OLDER} to fetch sessions before the
     *                       pointer,
     *                       or {@link QueryDirection#NEWER} to fetch sessions after the pointer.
     * @return A {@link BiScrollable} containing the list of session summaries (sorted reverse chronologically)
     *         and pointers for further scrolling.
     */
    public abstract BiScrollable<SessionSummary> sessions(int count,
                                                          String pointer,
                                                          QueryDirection queryDirection);

    /**
     * Implementation method for saving the session summary.
     * The session summary will be created if it doesn't exist.
     *
     * @param sessionSummary The session summary to save, with extra data applied.
     * @return The saved session summary, or empty if the save operation failed.
     */
    protected abstract Optional<SessionSummary> saveSessionImpl(SessionSummary sessionSummary);

}
