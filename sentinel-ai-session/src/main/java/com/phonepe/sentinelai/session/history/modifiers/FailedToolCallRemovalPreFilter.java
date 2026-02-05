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

import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Removes all failed tool call responses from the agent message history.
 */
@Slf4j
public class FailedToolCallRemovalPreFilter<R> implements MessagePersistencePreFilter<R> {
    private static final AgentMessageVisitor<String> FAILED_TOOL_CALL_FINDER = new AgentMessageVisitor<>() {
        @Override
        public String visit(AgentGenericMessage genericMessage) {
            return "";
        }

        @Override
        public String visit(AgentRequest request) {
            return request.accept(new AgentRequestVisitor<>() {
                @Override
                public String visit(SystemPrompt systemPrompt) {
                    return "";
                }

                @Override
                public String visit(ToolCallResponse toolCallResponse) {
                    return toolCallResponse.isSuccess() ? "" : toolCallResponse
                            .getToolCallId();
                }

                @Override
                public String visit(UserPrompt userPrompt) {
                    return "";
                }
            });
        }

        @Override
        public String visit(AgentResponse response) {
            return "";
        }
    };

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class FailedToolCallFilter implements AgentMessageVisitor<Boolean> {
        private final Set<String> failedCallIds;

        @Override
        public Boolean visit(AgentGenericMessage genericMessage) {
            return true;
        }

        @Override
        public Boolean visit(AgentRequest request) {
            return request.accept(new AgentRequestVisitor<>() {
                @Override
                public Boolean visit(SystemPrompt systemPrompt) {
                    return true;
                }

                @Override
                public Boolean visit(ToolCallResponse toolCallResponse) {
                    return !failedCallIds.contains(toolCallResponse
                            .getToolCallId());
                }

                @Override
                public Boolean visit(UserPrompt userPrompt) {
                    return true;
                }
            });
        }

        @Override
        public Boolean visit(AgentResponse response) {
            return response.accept(new AgentResponseVisitor<>() {
                @Override
                public Boolean visit(StructuredOutput structuredOutput) {
                    return true;
                }

                @Override
                public Boolean visit(Text text) {
                    return true;
                }

                @Override
                public Boolean visit(ToolCall toolCall) {
                    return !failedCallIds.contains(toolCall.getToolCallId());
                }
            });
        }
    }

    @Override
    public List<AgentMessage> filter(AgentRunContext<R> context,
                                     List<AgentMessage> agentMessages) {
        // Find all failed tool calls and then remove all the call requests and responses
        final var failedCallIds = agentMessages.stream()
                .map(message -> message.accept(FAILED_TOOL_CALL_FINDER))
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .collect(Collectors.toUnmodifiableSet());
        log.debug("Found failed tool call ids: {}", failedCallIds);
        return agentMessages.stream()
                .filter(agentMessage -> agentMessage.accept(
                                                            new FailedToolCallFilter(failedCallIds)))
                .toList();
    }
}
