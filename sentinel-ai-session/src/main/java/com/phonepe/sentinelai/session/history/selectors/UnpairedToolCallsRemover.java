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

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
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

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes messages related to tool calls which do not have both request and response.
 */
@Slf4j
public class UnpairedToolCallsRemover implements MessageSelector {

    @Data
    @RequiredArgsConstructor
    private static final class ToolCallData {
        private final String messageId;
        boolean hasRequest;
        boolean hasResponse;
    }

    @Override
    public List<AgentMessage> select(String sessionId,
                                     List<AgentMessage> messages) {
        final var toolCallDataMap = new HashMap<String, ToolCallData>();
        for (var message : messages) {
            message.accept(new AgentMessageVisitor<Void>() {
                @Override
                public Void visit(AgentGenericMessage genericMessage) {
                    return null;
                }

                @Override
                public Void visit(AgentRequest request) {
                    return request.accept(new AgentRequestVisitor<>() {

                        @Override
                        public Void visit(ToolCallResponse toolCallResponse) {
                            toolCallDataMap.computeIfAbsent(toolCallResponse
                                    .getToolCallId(),
                                                            k -> new ToolCallData(toolCallResponse
                                                                    .getMessageId()))
                                    .setHasResponse(true); //Tool Call executed and Response sent to model
                            return null;
                        }

                        @Override
                        public Void visit(UserPrompt userPrompt) {
                            return null;
                        }

                        @Override
                        public Void visit(com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt systemPrompt) {
                            return null;
                        }
                    });
                }

                @Override
                public Void visit(AgentResponse response) {
                    return response.accept(new AgentResponseVisitor<>() {
                        ;

                        @Override
                        public Void visit(StructuredOutput structuredOutput) {
                            return null;
                        }

                        @Override
                        public Void visit(Text text) {
                            return null;
                        }

                        @Override
                        public Void visit(ToolCall toolCall) {
                            toolCallDataMap.computeIfAbsent(toolCall
                                    .getToolCallId(),
                                                            k -> new ToolCallData(toolCall
                                                                    .getMessageId()))
                                    .setHasRequest(true); //Tool call execution request received from model
                            return null;
                        }
                    });
                }
            });
        }

        final var nonPairedCalls = toolCallDataMap.values()
                .stream()
                .filter(toolCallData -> toolCallData
                        .isHasRequest() != toolCallData.isHasResponse())
                .map(ToolCallData::getMessageId)
                .collect(Collectors.toUnmodifiableSet());
        log.debug("Found unpaired tool call message IDs: {}", nonPairedCalls);
        final var allowedMessages = new ArrayList<>(messages);
        allowedMessages.removeIf(message -> nonPairedCalls.contains(message
                .getMessageId()));
        log.debug("Returning {} messages for session {} after removing unpaired tool calls",
                  allowedMessages.size(),
                  sessionId);
        return allowedMessages;

    }
}
