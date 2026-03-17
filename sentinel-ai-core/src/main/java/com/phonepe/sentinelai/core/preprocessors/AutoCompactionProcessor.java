/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.sentinelai.core.preprocessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;
import com.phonepe.sentinelai.core.compaction.MessageCompactor;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessContext;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
@Builder
public class AutoCompactionProcessor implements AgentMessagesPreProcessor {

    private static final int DEFAULT_TOKEN_BUDGET = 1500;
    private static final int DEFAULT_COMPACTION_TRIGGER_THRESHOLD = 60;

    @Builder.Default
    private final CompactionPrompts prompts = CompactionPrompts.DEFAULT;
    @Builder.Default
    private final int tokenBudget = DEFAULT_TOKEN_BUDGET;
    @Builder.Default
    private final int compactionTriggerThresholdPercentage = DEFAULT_COMPACTION_TRIGGER_THRESHOLD;
    @Nullable
    private final Model model;

    @Override
    public AgentMessagesPreProcessResult process(AgentMessagesPreProcessContext ctx,
                                                 List<AgentMessage> allMessages,
                                                 List<AgentMessage> newMessages) {
        final var modelRunContext = ctx.getModelRunContext();
        final var latestMessage = newMessages.isEmpty() ? null : newMessages.get(newMessages.size() - 1);
        var compactionNeeded = latestMessage != null
                && latestMessage.getMessageType() != AgentMessageType.TOOL_CALL_REQUEST_MESSAGE
                && AgentUtils.isContextWindowThresholdBreached(allMessages,
                                                               modelRunContext.getAgentSetup(),
                                                               compactionTriggerThresholdPercentage);
        if (!compactionNeeded) {
            log.debug("Compaction not needed for agent {}. Proceeding without compaction.",
                      modelRunContext.getAgentName());
            return new AgentMessagesPreProcessResult(allMessages, newMessages);
        }
        final var compactionOutput = compactMessages(modelRunContext,
                                                     allMessages,
                                                     prompts,
                                                     tokenBudget,
                                                     model).orElse(null);
        if (null == compactionOutput) {
            log.warn("Message compaction failed for agent {}. Proceeding without compaction.",
                     modelRunContext.getAgentName());
            return new AgentMessagesPreProcessResult(allMessages, newMessages);
        }
        final var compactedPrompt = """
                Prompt to continue: %s
                Existing context: %s
                """.formatted(compactionOutput.getContinuation(), compactionOutput.getRawData());
        final var outputAllMessages = new ArrayList<AgentMessage>(allMessages);
        final var outputNewMessages = new ArrayList<AgentMessage>(newMessages);
        final var userPrompt = new UserPrompt(modelRunContext.getSessionId(),
                                              modelRunContext.getRunId(),
                                              compactedPrompt,
                                              true,
                                              LocalDateTime.now());
        outputAllMessages.add(userPrompt);
        outputNewMessages.add(userPrompt);
        return new AgentMessagesPreProcessResult(outputAllMessages, outputNewMessages);
    }

    private static Optional<ExtractedSummary> compactMessages(ModelRunContext modelRunContext,
                                                              List<AgentMessage> allMessages,
                                                              CompactionPrompts prompts,
                                                              int tokenBudget,
                                                              Model model) {
        try {
            final var agentSetup = null == model
                    ? modelRunContext.getAgentSetup()
                    : modelRunContext.getAgentSetup().withModel(model);
            return MessageCompactor.compactMessages(modelRunContext.getAgentName(),
                                                    modelRunContext.getSessionId(),
                                                    modelRunContext.getUserId(),
                                                    agentSetup,
                                                    agentSetup.getMapper(),
                                                    modelRunContext.getModelUsageStats(),
                                                    allMessages,
                                                    prompts,
                                                    tokenBudget)
                    .get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Message compaction interrupted for agent {}: {}",
                      modelRunContext.getAgentName(),
                      e.getMessage(),
                      e);
        }
        catch (Exception e) {
            log.error("Error during message compaction for agent {}: {}",
                      modelRunContext.getAgentName(),
                      AgentUtils.rootCause(e).getMessage(),
                      e);
        }
        return Optional.empty();
    }
}
