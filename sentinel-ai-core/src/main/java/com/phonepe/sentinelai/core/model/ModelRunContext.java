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

package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.model.transformer.RequestTransformer;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context object passed to the model at runtime.
 */
@Value
@Builder
public class ModelRunContext {
    /**
     * Name of the agent that is running this model
     */
    String agentName;
    /**
     * An id for this particular run. This is used to track the run in logs and events
     */
    String runId;

    /**
     * Session id for this run. This is used to track the run in logs and events
     */
    String sessionId;

    /**
     * User id for this run. This is used to track the run in logs and events
     */
    String userId;

    /**
     * Required setup for the agent
     */
    AgentSetup agentSetup;

    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

    /**
     * Processing mode for this run
     */
    ProcessingMode processingMode;

    /**
     * Request transformers contributed by the agent extensions of this run; may be empty. The
     * model applies them before the provider level and model level transformers.
     */
    List<RequestTransformer> requestTransformers;

    /**
     * Ids of the messages of this run that were raised already in a sent-message event. A
     * retry of the run re-enters the model loop; this tracker keeps each message of the
     * run raised exactly once. Never null.
     */
    @Builder.Default
    Set<String> raisedMessageIds = ConcurrentHashMap.newKeySet();

    /**
     * Creates a run context without extension request transformers.
     *
     * @param agentName       name of the agent that runs the model
     * @param runId           id of this run
     * @param sessionId       session id of this run
     * @param userId          user id of this run
     * @param agentSetup      setup of the agent
     * @param modelUsageStats usage stats of this run
     * @param processingMode  processing mode of this run
     */
    public ModelRunContext(final String agentName,
                           final String runId,
                           final String sessionId,
                           final String userId,
                           final AgentSetup agentSetup,
                           final ModelUsageStats modelUsageStats,
                           final ProcessingMode processingMode) {
        this(agentName, runId, sessionId, userId, agentSetup, modelUsageStats, processingMode, List.of());
    }

    /**
     * Creates a run context.
     *
     * @param agentName           name of the agent that runs the model
     * @param runId               id of this run
     * @param sessionId           session id of this run
     * @param userId              user id of this run
     * @param agentSetup          setup of the agent
     * @param modelUsageStats     usage stats of this run
     * @param requestTransformers extension request transformers; null means none
     */
    public ModelRunContext(final String agentName,
                           final String runId,
                           final String sessionId,
                           final String userId,
                           final AgentSetup agentSetup,
                           final ModelUsageStats modelUsageStats,
                           final ProcessingMode processingMode,
                           final List<RequestTransformer> requestTransformers) {
        this(agentName,
             runId,
             sessionId,
             userId,
             agentSetup,
             modelUsageStats,
             processingMode,
             requestTransformers,
             ConcurrentHashMap.newKeySet());
    }

    /**
     * Creates a run context.
     *
     * @param agentName           name of the agent that runs the model
     * @param runId               id of this run
     * @param sessionId           session id of this run
     * @param userId              user id of this run
     * @param agentSetup          setup of the agent
     * @param modelUsageStats     usage stats of this run
     * @param processingMode      processing mode of this run
     * @param requestTransformers extension request transformers; null means none
     * @param raisedMessageIds    tracker of the message ids raised in sent-message events;
     *                            null means a new tracker
     */
    public ModelRunContext(final String agentName,
                           final String runId,
                           final String sessionId,
                           final String userId,
                           final AgentSetup agentSetup,
                           final ModelUsageStats modelUsageStats,
                           final ProcessingMode processingMode,
                           final List<RequestTransformer> requestTransformers,
                           final Set<String> raisedMessageIds) {
        this.agentName = agentName;
        this.runId = runId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.agentSetup = agentSetup;
        this.modelUsageStats = modelUsageStats;
        this.processingMode = processingMode;
        this.requestTransformers = requestTransformers == null ? List.of() : List.copyOf(requestTransformers);
        this.raisedMessageIds = Objects.requireNonNullElseGet(raisedMessageIds,
                                                              ConcurrentHashMap::newKeySet);
    }
}
