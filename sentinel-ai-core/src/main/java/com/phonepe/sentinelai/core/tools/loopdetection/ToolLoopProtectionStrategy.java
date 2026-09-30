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

package com.phonepe.sentinelai.core.tools.loopdetection;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse.ResponseType;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse.doNotTerminate;
import static com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse.terminate;

/**
 * An {@link EarlyTerminationStrategy} that wraps a delegate strategy and adds tool loop
 * protection on top of it. The protection is stateful and scoped to a single model run:
 * create one instance per run.
 *
 * <p>The strategy detects the tool calls of the latest model round from the new messages
 * of the model output and feeds them to a {@link ToolLoopProtection}. When the protection
 * detects a loop, the strategy first instructs the model, then terminates the run. Budget
 * breaches terminate the run immediately.</p>
 *
 * <p>The delegate strategy runs first. A TERMINATE decision of the delegate wins over the
 * protection decisions.</p>
 */
@Slf4j
@AllArgsConstructor
public class ToolLoopProtectionStrategy implements EarlyTerminationStrategy {

    private final EarlyTerminationStrategy delegate;
    private final ToolLoopProtection protection;

    /**
     * Extracts the tool calls of the latest model round from the new messages of the output.
     *
     * @param output The current model output.
     * @return The tool calls of the latest round.
     */
    private static List<ToolCall> latestRoundToolCalls(ModelOutput output) {
        if (null == output || null == output.getNewMessages()) {
            return List.of();
        }
        return output.getNewMessages()
                .stream()
                .filter(ToolCall.class::isInstance)
                .map(ToolCall.class::cast)
                .toList();
    }

    @Override
    public EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings,
                                                     final ModelRunContext modelRunContext,
                                                     final ModelOutput output) {
        final var delegateResponse = Optional.ofNullable(delegate)
                .map(strategy -> strategy.evaluate(modelSettings, modelRunContext, output))
                .orElse(null);
        if (null != delegateResponse
                && (delegateResponse.getResponseType() == ResponseType.TERMINATE
                        || delegateResponse.getResponseType() == ResponseType.INSTRUCT)) {
            return delegateResponse;
        }

        final var decision = protection.recordRound(latestRoundToolCalls(output));
        return switch (decision.getType()) {
            case TERMINATE_LOOP -> terminate(ErrorType.TOOL_LOOP_DETECTED,
                                             "Tool loop detected: model rounds repeated the same tool calls %d times"
                                                     .formatted(decision.getRoundCount()));
            case TERMINATE_BUDGET -> terminate(ErrorType.TOOL_CALL_BUDGET_EXCEEDED, decision.getDetail());
            case INSTRUCT -> EarlyTerminationStrategyResponse.instructWithFeedback(decision.instruction());
            case CONTINUE -> Objects.requireNonNullElse(delegateResponse, doNotTerminate());
        };
    }
}
