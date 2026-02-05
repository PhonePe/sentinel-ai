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

package com.phonepe.sentinelai.core.compaction;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@UtilityClass
public class MessageCompactor {

    private static final String OUTPUT_KEY = "sessionOutput";

    public static CompletableFuture<Optional<ExtractedSummary>> compactMessages(final String agentName,
                                                                                final String sessionId,
                                                                                final String userId,
                                                                                final AgentSetup agentSetup,
                                                                                final ObjectMapper mapper,
                                                                                final ModelUsageStats stats,
                                                                                final List<AgentMessage> messages) {
        final var runId = "message-summarization-" + UUID.randomUUID();
        final var usageStats = Objects.requireNonNullElseGet(stats,
                                                             ModelUsageStats::new);
        final var modelRunContext = new ModelRunContext(agentName,
                                                        runId,
                                                        sessionId,
                                                        userId,
                                                        agentSetup
                                                                .withModelSettings(agentSetup
                                                                        .getModelSettings()
                                                                        .withParallelToolCalls(false)),
                                                        usageStats,
                                                        ProcessingMode.DIRECT);
        return agentSetup.getModel()
                .compute(modelRunContext,
                         List.of(sessionSummarySchema()),
                         messages,
                         Map.of(),
                         new NonContextualDefaultExternalToolRunner(sessionId,
                                                                    runId,
                                                                    mapper),
                         new NeverTerminateEarlyStrategy(),
                         List.of())
                .thenApply(output -> {
                    log.debug("Summarization usage: {}", usageStats);
                    if (output.getError() != null && !output.getError()
                            .getErrorType()
                            .equals(ErrorType.SUCCESS)) {
                        log.error("Error extracting session summary: {}",
                                  output.getError());
                    }
                    else {
                        final var summaryData = output.getData()
                                .get(OUTPUT_KEY);
                        if (JsonUtils.empty(summaryData)) {
                            log.debug("No summary extracted from the output");
                        }
                        else {
                            log.debug("Extracted session summary output from summarization run: {}",
                                      summaryData);
                            try {
                                return Optional.of(mapper.treeToValue(
                                                                      summaryData,
                                                                      ExtractedSummary.class));
                            }
                            catch (Exception e) {
                                log.error("Error extracting summary: %s"
                                        .formatted(e.getMessage()), e);
                            }
                        }
                    }
                    return Optional.<ExtractedSummary>empty();
                });
    }

    private static ModelOutputDefinition sessionSummarySchema() {
        return new ModelOutputDefinition(OUTPUT_KEY,
                                         "Schema summary for this session and run",
                                         JsonUtils.schema(
                                                          ExtractedSummary.class));
    }


}
