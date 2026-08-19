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

package com.phonepe.sentinelai.core.preprocessors;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.AutoCompactionSetup;
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
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.phonepe.sentinelai.core.utils.AgentUtils.isContextWindowThresholdBreached;
import static com.phonepe.sentinelai.core.utils.AgentUtils.messagesAfterLastCompaction;

@Slf4j
public class AutoCompactionProcessor implements AgentMessagesPreProcessor {
    private final CompactionPrompts prompts;

    private final int tokenBudget;
    private final int compactionTriggerThresholdPercentage;
    private final Model model;
    private final ModelSettings modelSettings;
    private final boolean skipToolMessages;

    @Builder
    public AutoCompactionProcessor(@NonNull AutoCompactionSetup setup) {
        this.prompts = setup.getPrompts();
        this.compactionTriggerThresholdPercentage = setup.getCompactionTriggerThresholdPercentage();
        this.tokenBudget = setup.getTokenBudget();
        this.model = setup.getModel();
        this.modelSettings = setup.getModelSettings();
        this.skipToolMessages = setup.isSkipToolMessages();
    }

    private static AgentSetup applyCompactionOverrides(AgentSetup agentSetup,
                                                       Model model,
                                                       ModelSettings modelSettings) {
        var result = agentSetup;
        if (model != null) {
            result = result.withModel(model);
        }
        if (modelSettings != null) {
            result = result.withModelSettings(modelSettings);
        }
        return result;
    }

    private static Optional<ExtractedSummary> compactMessages(ModelRunContext modelRunContext,
                                                              List<AgentMessage> allMessages,
                                                              CompactionPrompts prompts,
                                                              int tokenBudget,
                                                              boolean skipToolMessages,
                                                              Model model,
                                                              ModelSettings modelSettings) {
        try {
            final var sessionAgentSetup = modelRunContext.getAgentSetup();
            final var sessionModelSettings = sessionAgentSetup.getModelSettings();
            final var sessionModelAttributes = sessionModelSettings != null
                    ? sessionModelSettings.getModelAttributes()
                    : ModelAttributes.DEFAULT_MODEL_ATTRIBUTES;
            final var sessionContextWindowSize = sessionModelAttributes.getContextWindowSize();

            var effectiveModel = model;
            var effectiveModelSettings = modelSettings;

            // Safety check: if a compaction model is provided, verify its context window
            // is at least as large as the session model's context window. If not, fall back
            // to the session model and session model settings.
            if (model != null) {
                final var compactorModelAttributes = modelSettings != null
                        ? modelSettings.getModelAttributes()
                        : sessionModelAttributes;
                final var compactorContextWindowSize = compactorModelAttributes.getContextWindowSize();
                if (compactorContextWindowSize < sessionContextWindowSize) {
                    log.warn("Compaction model context window size ({}) is smaller than session model context "
                            + "window size ({}). Falling back to session model for compaction of agent {}.",
                             compactorContextWindowSize,
                             sessionContextWindowSize,
                             modelRunContext.getAgentName());
                    effectiveModel = null;
                    effectiveModelSettings = null;
                }
            }

            final var effectiveSetup = applyCompactionOverrides(sessionAgentSetup,
                                                                effectiveModel,
                                                                effectiveModelSettings);

            return MessageCompactor.compactMessages(modelRunContext.getAgentName(),
                                                    modelRunContext.getSessionId(),
                                                    modelRunContext.getUserId(),
                                                    effectiveSetup,
                                                    effectiveSetup.getMapper(),
                                                    modelRunContext.getModelUsageStats(),
                                                    allMessages,
                                                    prompts,
                                                    tokenBudget,
                                                    skipToolMessages)
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

    @SneakyThrows
    private static String continuationPrompt(final ExtractedSummary compactionOutput,
                                             final ObjectMapper mapper) {
        return mapper.writeValueAsString(mapper.createObjectNode()
                .set("user_input", compactionOutput.getRawData()));
    }

    @Override
    public AgentMessagesPreProcessResult process(AgentMessagesPreProcessContext ctx,
                                                 List<AgentMessage> allMessages,
                                                 List<AgentMessage> newMessages) {
        final var modelRunContext = ctx.getModelRunContext();
        final var latestMessage = allMessages.isEmpty() ? null : allMessages.get(allMessages.size() - 1);
        final var messagesAfterLastCompaction = messagesAfterLastCompaction(allMessages);
        log.debug("Checking if compaction is needed for agent {}. All message count: {}, Relevant messages count: {}, token budget: {}, threshold percentage: {}",
                  modelRunContext.getAgentName(),
                  allMessages.size(),
                  messagesAfterLastCompaction.size(),
                  tokenBudget,
                  compactionTriggerThresholdPercentage);
        var compactionNeeded = latestMessage != null
                && latestMessage.getMessageType() != AgentMessageType.TOOL_CALL_REQUEST_MESSAGE
                && isContextWindowThresholdBreached(messagesAfterLastCompaction,
                                                    modelRunContext.getAgentSetup(),
                                                    compactionTriggerThresholdPercentage);
        if (!compactionNeeded) {
            log.debug("Compaction not needed for agent {}. Proceeding without compaction.",
                      modelRunContext.getAgentName());
            return new AgentMessagesPreProcessResult(null, newMessages);
        }
        final var compactionOutput = compactMessages(modelRunContext,
                                                     messagesAfterLastCompaction,
                                                     prompts,
                                                     tokenBudget,
                                                     skipToolMessages,
                                                     model,
                                                     modelSettings).orElse(null);
        if (null == compactionOutput) {
            log.warn("Message compaction failed for agent {}. Proceeding without compaction.",
                     modelRunContext.getAgentName());
            return new AgentMessagesPreProcessResult(null, newMessages);
        }
        final var mapper = modelRunContext.getAgentSetup().getMapper();
        final var outputAllMessages = new ArrayList<AgentMessage>(allMessages);
        final var outputNewMessages = new ArrayList<AgentMessage>(newMessages);
        final var userPrompt = new UserPrompt(modelRunContext.getSessionId(),
                                              modelRunContext.getRunId(),
                                              continuationPrompt(compactionOutput, mapper),
                                              true,
                                              LocalDateTime.now());
        outputAllMessages.add(userPrompt);
        outputNewMessages.add(userPrompt);
        return new AgentMessagesPreProcessResult(outputAllMessages, outputNewMessages);
    }

}
