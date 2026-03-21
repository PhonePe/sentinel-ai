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

package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.model.Model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import javax.annotation.Nullable;

/**
 * Details to setup auto compaction for an agent.
 */
@Value
@Builder
@With
public class AutoCompactionSetup {
    public static final AutoCompactionSetup DEFAULT = AutoCompactionSetup.builder().build();

    public static final int DEFAULT_TOKEN_BUDGET = 1500;
    public static final int DEFAULT_COMPACTION_TRIGGER_THRESHOLD = 60;

    @Builder.Default
    @NonNull
    private final CompactionPrompts prompts = CompactionPrompts.DEFAULT;

    @Builder.Default
    private final int tokenBudget = DEFAULT_TOKEN_BUDGET;

    @Builder.Default
    private final int compactionTriggerThresholdPercentage = DEFAULT_COMPACTION_TRIGGER_THRESHOLD;

    @Nullable
    private final Model model;

    public AutoCompactionSetup merge(@Nullable AutoCompactionSetup other) {
        if (other == null) {
            return this;
        }
        return AutoCompactionSetup.builder()
                .prompts(other.prompts != null ? other.prompts : this.prompts)
                .tokenBudget(other.tokenBudget != DEFAULT_TOKEN_BUDGET ? other.tokenBudget : this.tokenBudget)
                .compactionTriggerThresholdPercentage(other.compactionTriggerThresholdPercentage
                        != DEFAULT_COMPACTION_TRIGGER_THRESHOLD
                                ? other.compactionTriggerThresholdPercentage
                                : this.compactionTriggerThresholdPercentage)
                .model(other.model != null ? other.model : this.model)
                .build();
    }
}
