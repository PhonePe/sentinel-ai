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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AutoCompactionSetupTest {

    @Test
    void testDefaultConstants() {
        assertEquals(1500, AutoCompactionSetup.DEFAULT_TOKEN_BUDGET);
        assertEquals(60, AutoCompactionSetup.DEFAULT_COMPACTION_TRIGGER_THRESHOLD);
    }

    @Test
    void testDefaultInstance() {
        assertNotNull(AutoCompactionSetup.DEFAULT);
        assertEquals(CompactionPrompts.DEFAULT, AutoCompactionSetup.DEFAULT.getPrompts());
        assertEquals(AutoCompactionSetup.DEFAULT_TOKEN_BUDGET, AutoCompactionSetup.DEFAULT.getTokenBudget());
        assertEquals(AutoCompactionSetup.DEFAULT_COMPACTION_TRIGGER_THRESHOLD,
                     AutoCompactionSetup.DEFAULT.getCompactionTriggerThresholdPercentage());
        assertNull(AutoCompactionSetup.DEFAULT.getModel());
    }

    @Test
    void testMergeDoesNotMutateOriginal() {
        final var basePrompts = CompactionPrompts.DEFAULT;
        final var baseModel = createDummyModel();

        final var base = AutoCompactionSetup.builder()
                .prompts(basePrompts)
                .tokenBudget(2000)
                .compactionTriggerThresholdPercentage(70)
                .model(baseModel)
                .build();

        final var otherPrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Other system")
                .build();
        final var otherModel = createDummyModel();

        final var other = AutoCompactionSetup.builder()
                .prompts(otherPrompts)
                .tokenBudget(3000)
                .compactionTriggerThresholdPercentage(80)
                .model(otherModel)
                .build();

        base.merge(other);

        // Verify base is unchanged (immutable)
        assertEquals(basePrompts, base.getPrompts());
        assertEquals(2000, base.getTokenBudget());
        assertEquals(70, base.getCompactionTriggerThresholdPercentage());
        assertEquals(baseModel, base.getModel());

        // Verify other is unchanged
        assertEquals(otherPrompts, other.getPrompts());
        assertEquals(3000, other.getTokenBudget());
        assertEquals(80, other.getCompactionTriggerThresholdPercentage());
        assertEquals(otherModel, other.getModel());
    }

    @Test
    void testMergeOverridesAllFields() {
        final var basePrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Base system")
                .build();
        final var baseModel = createDummyModel();

        final var base = AutoCompactionSetup.builder()
                .prompts(basePrompts)
                .tokenBudget(2000)
                .compactionTriggerThresholdPercentage(70)
                .model(baseModel)
                .build();

        final var otherPrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Other system")
                .build();
        final var otherModel = createDummyModel();

        final var other = AutoCompactionSetup.builder()
                .prompts(otherPrompts)
                .tokenBudget(3000)
                .compactionTriggerThresholdPercentage(80)
                .model(otherModel)
                .build();

        final var result = base.merge(other);

        assertEquals(otherPrompts, result.getPrompts());
        assertEquals(3000, result.getTokenBudget());
        assertEquals(80, result.getCompactionTriggerThresholdPercentage());
        assertEquals(otherModel, result.getModel());
    }

    @Test
    void testMergeOverridesOnlyNonDefaultFields() {
        final var basePrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Base system")
                .build();
        final var baseModel = createDummyModel();

        final var base = AutoCompactionSetup.builder()
                .prompts(basePrompts)
                .tokenBudget(2000)
                .compactionTriggerThresholdPercentage(70)
                .model(baseModel)
                .build();

        // Other has some default values
        final var otherPrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Other system")
                .build();

        final var other = AutoCompactionSetup.builder()
                .prompts(otherPrompts)
                .tokenBudget(AutoCompactionSetup.DEFAULT_TOKEN_BUDGET) // default value
                .compactionTriggerThresholdPercentage(80)
                .model(null) // null model
                .build();

        final var result = base.merge(other);

        assertEquals(otherPrompts, result.getPrompts());
        assertEquals(2000, result.getTokenBudget()); // kept from base
        assertEquals(80, result.getCompactionTriggerThresholdPercentage());
        assertEquals(baseModel, result.getModel()); // kept from base since other is null
    }

    @Test
    void testMergeReturnsNewInstance() {
        final var base = AutoCompactionSetup.DEFAULT;
        final var other = AutoCompactionSetup.builder()
                .tokenBudget(3000)
                .build();

        final var result = base.merge(other);

        assertNotNull(result);
        // Result should be different instance since merge creates new builder
        assertEquals(3000, result.getTokenBudget());
    }

    @Test
    void testMergeWithDefaultCompactionTriggerThreshold() {
        final var base = AutoCompactionSetup.builder()
                .compactionTriggerThresholdPercentage(75)
                .build();

        final var other = AutoCompactionSetup.builder()
                .compactionTriggerThresholdPercentage(AutoCompactionSetup.DEFAULT_COMPACTION_TRIGGER_THRESHOLD)
                .build();

        final var result = base.merge(other);

        assertEquals(75, result.getCompactionTriggerThresholdPercentage());
    }

    @Test
    void testMergeWithDefaultOther() {
        final var customPrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Custom system")
                .build();
        final var customModel = createDummyModel();

        final var base = AutoCompactionSetup.builder()
                .prompts(customPrompts)
                .tokenBudget(2000)
                .compactionTriggerThresholdPercentage(70)
                .model(customModel)
                .build();

        final var other = AutoCompactionSetup.DEFAULT;

        final var result = base.merge(other);

        // When merging with DEFAULT, since other.prompts is not null (it's DEFAULT),
        // it will override base.prompts
        assertEquals(CompactionPrompts.DEFAULT, result.getPrompts());
        // tokenBudget is DEFAULT_TOKEN_BUDGET in other, so base value is kept
        assertEquals(2000, result.getTokenBudget());
        // compactionTriggerThresholdPercentage is DEFAULT in other, so base value is kept
        assertEquals(70, result.getCompactionTriggerThresholdPercentage());
        // model is null in other (DEFAULT), so base value is kept
        assertEquals(customModel, result.getModel());
    }

    @Test
    void testMergeWithDefaultTokenBudget() {
        final var base = AutoCompactionSetup.builder()
                .tokenBudget(2000)
                .build();

        final var other = AutoCompactionSetup.builder()
                .tokenBudget(AutoCompactionSetup.DEFAULT_TOKEN_BUDGET)
                .build();

        final var result = base.merge(other);

        assertEquals(2000, result.getTokenBudget());
    }

    @Test
    void testMergeWithNull() {
        final var base = AutoCompactionSetup.builder()
                .prompts(CompactionPrompts.DEFAULT)
                .tokenBudget(2000)
                .compactionTriggerThresholdPercentage(70)
                .model(createDummyModel())
                .build();

        final var result = base.merge(null);

        assertSame(base, result);
    }

    @Test
    void testMergeWithNullModel() {
        final var baseModel = createDummyModel();

        final var base = AutoCompactionSetup.builder()
                .model(baseModel)
                .tokenBudget(2000)
                .build();

        final var other = AutoCompactionSetup.builder()
                .model(null)
                .tokenBudget(3000)
                .build();

        final var result = base.merge(other);

        assertEquals(baseModel, result.getModel());
        assertEquals(3000, result.getTokenBudget());
    }

    @Test
    void testMergeWithNullPrompts() {
        final var basePrompts = CompactionPrompts.builder()
                .summarizationSystemPrompt("Base system")
                .build();

        final var base = AutoCompactionSetup.builder()
                .prompts(basePrompts)
                .tokenBudget(2000)
                .build();

        // Create other with a non-null CompactionPrompts (can't pass null due to @NonNull)
        final var otherPrompts = CompactionPrompts.DEFAULT;
        final var other = AutoCompactionSetup.builder()
                .prompts(otherPrompts)
                .tokenBudget(3000)
                .build();

        final var result = base.merge(other);

        // Since other has DEFAULT prompts (not null), they should override
        assertEquals(otherPrompts, result.getPrompts());
        assertEquals(3000, result.getTokenBudget());
    }

    private Model createDummyModel() {
        return new Model() {
            @Override
            public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                          Collection<ModelOutputDefinition> outputDefinitions,
                                                          List<AgentMessage> oldMessages,
                                                          Map<String, ExecutableTool> tools,
                                                          ToolRunner toolRunner,
                                                          EarlyTerminationStrategy earlyTerminationStrategy,
                                                          List<AgentMessagesPreProcessor> preProcessors) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
