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
import com.phonepe.sentinelai.core.events.AgentEventVisitor;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import lombok.Value;

import java.util.List;
import java.util.Optional;

public class AgentEventMessageExtractor implements
        AgentEventVisitor<Optional<AgentEventMessageExtractor.ExtractedData>> {
    @Value
    public static class ExtractedData {
        List<AgentMessage> newMessages;
        List<AgentMessage> allMessages;
    }

    @Override
    public Optional<ExtractedData> visit(InputReceivedAgentEvent inputReceived) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtractedData> visit(MessageReceivedAgentEvent messageReceived) {
        return Optional.of(new ExtractedData(messageReceived.getNewMessages(), messageReceived.getAllMessages()));
    }

    @Override
    public Optional<ExtractedData> visit(MessageSentAgentEvent messageSent) {
        return Optional.of(new ExtractedData(messageSent.getNewMessages(), messageSent.getAllMessages()));
    }

    @Override
    public Optional<ExtractedData> visit(OutputErrorAgentEvent outputErrorAgentEvent) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtractedData> visit(OutputGeneratedAgentEvent outputGeneratedAgentEvent) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtractedData> visit(ToolCallApprovalDeniedAgentEvent toolCallApprovalDenied) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtractedData> visit(ToolCallCompletedAgentEvent toolCallCompleted) {
        return Optional.empty();
    }

    @Override
    public Optional<ExtractedData> visit(ToolCalledAgentEvent toolCalled) {
        return Optional.empty();
    }

}
