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

import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads messages from the session store.
 * This class exists for the sole purpose making the process of reading messages in correct manner testable.
 */
@UtilityClass
@Slf4j
public class MessageReadingUtils {

    /**
     * Reads messages since the given message id.
     *
     * @param sessionId               Session Id
     * @param lastSummarizedMessageId Last summarized message id
     * @param skipSystemPrompt        Whether to skip system prompt messages
     * @return BiScrollable of AgentMessages
     */
    public static BiScrollable<AgentMessage> readMessagesSinceId(final SessionStore sessionStore,
            final AgentSessionExtensionSetup setup, final String sessionId, final String lastSummarizedMessageId,
            final boolean skipSystemPrompt, final List<MessageSelector> messageSelectors) {
        var pointer = "";
        var messagesInThisBatch = List.<AgentMessage>of();
        var newPointer = "";
        BiScrollable<AgentMessage> response = null;
        final var fetchCount = Math.min(AgentSessionExtensionSetup.MAX_HISTORICAL_MESSAGES_FETCH_COUNT, Math.max(1,
                setup.getHistoricalMessageFetchSize()));

        log.debug("Reading messages since id {} for session {}, {} messages per page", lastSummarizedMessageId,
                sessionId, fetchCount);

        final var messagesFromLastSummary = new ArrayList<AgentMessage>();

        // This loop reads messages in batches going back in time.
        // Each batch is returned in chronological order (oldest to newest).
        // Apply filters after accumulating all messages since last Summary
        // This ensures that filters that need holistic view of history can work correctly
        do {
            response = sessionStore.readMessages(sessionId, fetchCount, skipSystemPrompt, AgentUtils.getIfNotNull(
                    response, BiScrollable::getPointer, null), QueryDirection.OLDER);
            newPointer = Strings.isNullOrEmpty(newPointer) ? response.getPointer().getNewer() : newPointer;
            final var batch = response.getItems();
            pointer = response.getPointer().getOlder();
            if (batch.isEmpty()) {
                break;
            }
            messagesInThisBatch = batch;

            // Check if we have reached lastSummarizedMessageId
            if (lastSummarizedMessageId != null) {
                var foundIndex = -1;
                for (int i = 0; i < batch.size(); i++) {
                    if (batch.get(i).getMessageId().equals(lastSummarizedMessageId)) {
                        foundIndex = i;
                        break;
                    }
                }
                if (foundIndex != -1) {
                    // Add messages strictly newer than lastSummarizedMessageId (indices foundIndex+1 to batch.size()-1)
                    if (foundIndex + 1 < batch.size()) {
                        messagesFromLastSummary.addAll(batch.subList(foundIndex + 1, batch.size()));
                    }
                    break;
                }
            }
            messagesFromLastSummary.addAll(batch);

        } while (messagesInThisBatch.size() == fetchCount && !Strings.isNullOrEmpty(pointer));

        // Sort all accumulated messages chronologically from oldest to newest
        List<AgentMessage> chronological = messagesFromLastSummary.stream()
                .sorted(Comparator.comparing(AgentMessage::getTimestamp).thenComparing(AgentMessage::getMessageId))
                .toList();
        for (final var filter : messageSelectors) {
            chronological = filter.select(sessionId, chronological);
        }

        return new BiScrollable<>(List.copyOf(chronological), new BiScrollable.DataPointer(pointer, newPointer));
    }

    /**
     * Rearranges tool call messages to ensure that each tool call request is immediately followed by its response.
     *
     * @param outputMessages List of messages to rearrange
     * @return Rearranged list of messages
     */
    public static List<AgentMessage> rearrangeMessages(final List<AgentMessage> outputMessages) {
        final var toolCallIds = groupToolCallMessages(outputMessages);
        final var rearrangedMessages = new ArrayList<AgentMessage>();
        final var processedToolCallIds = new HashSet<String>();
        for (final var message : outputMessages) {
            switch (message.getMessageType()) {
                case TOOL_CALL_REQUEST_MESSAGE, TOOL_CALL_RESPONSE_MESSAGE -> {
                    final var key = toolCallId(message);
                    if (!processedToolCallIds.contains(key) && toolCallIds.containsKey(key)) {
                        final var msgs = toolCallIds.get(key);
                        if (msgs.size() != 2) {
                            log.warn("Tool call id {} does not have both request and response. Messages: {}", key,
                                    msgs);
                        }
                        else {
                            rearrangedMessages.add(msgs.get(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE));
                            rearrangedMessages.add(msgs.get(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE));
                        }
                        processedToolCallIds.add(key);
                    }
                }
                default -> rearrangedMessages.add(message);
            }
        }
        return rearrangedMessages;
    }

    /**
     * Regroups tool call messages by their tool call ids
     *
     * @param messages Message list
     * @return Map of tool call ids to their request and response messages
     */
    private static Map<String, Map<AgentMessageType, AgentMessage>> groupToolCallMessages(
            final List<AgentMessage> messages) {
        //Rearrange messages to put the tool call and its response one after the other
        final var toolCallIds = new TreeMap<String, Map<AgentMessageType, AgentMessage>>();
        //Construct the map by iterating over outputMessages
        messages.forEach(outputMessage -> {
            switch (outputMessage.getMessageType()) {
                case TOOL_CALL_REQUEST_MESSAGE, TOOL_CALL_RESPONSE_MESSAGE -> {
                    final var key = toolCallId(outputMessage);
                    if (!Strings.isNullOrEmpty(key)) {
                        toolCallIds.computeIfAbsent(key, id -> new HashMap<>())
                                .put(outputMessage.getMessageType(), outputMessage);
                    }
                    else {
                        log.warn("Tool call message with empty tool call id found: {}", outputMessage);
                    }
                }
                default -> {
                    //Do nothing
                }
            }
        });
        return toolCallIds;
    }

    /**
     * Extracts tool call IDs from list of messages
     *
     * @param message Agent message
     * @return A tool call id for the message or empty string
     */
    private static String toolCallId(final AgentMessage message) {
        return message.accept(new AgentMessageVisitor<>() {
            @Override
            public String visit(AgentGenericMessage genericMessage) {
                return "";
            }

            @Override
            public String visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {

                    @Override
                    public String visit(ToolCallResponse toolCallResponse) {
                        return toolCallResponse.getToolCallId();
                    }

                    @Override
                    public String visit(UserPrompt userPrompt) {
                        return "";
                    }

                    @Override
                    public String visit(com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt systemPrompt) {
                        return "";
                    }
                });
            }

            @Override
            public String visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<String>() {
                    @Override
                    public String visit(StructuredOutput structuredOutput) {
                        return "";
                    }

                    @Override
                    public String visit(Text text) {
                        return "";
                    }

                    @Override
                    public String visit(ToolCall toolCall) {
                        return toolCall.getToolCallId();
                    }
                });
            }
        });
    }


}
