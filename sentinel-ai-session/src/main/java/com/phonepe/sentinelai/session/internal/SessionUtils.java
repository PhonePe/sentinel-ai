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

package com.phonepe.sentinelai.session.internal;

import com.phonepe.sentinelai.core.agent.Agent.ProcessingCompletedData;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.session.AgentSessionExtensionSetup;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@UtilityClass
@Slf4j
public class SessionUtils {

    public static boolean isAlreadyLengthExceeded(final ProcessingCompletedData<?, ?, ?> data) {
        if (data.getOutput()
                .getError()
                .getErrorType()
                .equals(ErrorType.LENGTH_EXCEEDED)) {
            log.warn("Compaction needed as the last run ended with LENGTH_EXCEEDED error.");
            return true;
        }
        return false;
    }

    public static boolean isContextWindowThresholdBreached(final List<AgentMessage> messages,
                                                           final AgentSetup agentSetup,
                                                           final AgentSessionExtensionSetup extensionSetup) {
        final var threshold = extensionSetup.getAutoSummarizationThresholdPercentage();
        if (threshold == 0) {
            log.debug("Compaction needed as threshold is set to 0 (Every Run).");
            return true;
        }
        final var estimateTokenCount = agentSetup
                .getModel()
                .estimateTokenCount(messages, agentSetup);
        final var modelAttributes = Objects.requireNonNullElse(agentSetup.getModelSettings()
                .getModelAttributes(), ModelAttributes.DEFAULT_MODEL_ATTRIBUTES);
        final var contextWindowSize = modelAttributes
                .getContextWindowSize();
        final var currentBoundary = (contextWindowSize * threshold) / 100;
        final var evalResult = estimateTokenCount >= currentBoundary;
        log.debug("Automatic summarization evaluation: estimatedTokenCount={}, contextWindowSize={}, "
                + "threshold={}%, currentBoundary={}, needsSummarization={}",
                  estimateTokenCount,
                  contextWindowSize,
                  threshold,
                  currentBoundary,
                  evalResult);
        return evalResult;
    }

}
