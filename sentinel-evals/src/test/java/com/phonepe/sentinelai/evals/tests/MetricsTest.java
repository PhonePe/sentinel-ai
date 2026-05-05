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

package com.phonepe.sentinelai.evals.tests;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceBySimilarityMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputSimilarityMetric;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsTest {

    private static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public void close() {
            // no-op
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public float[] getEmbedding(String input) {
            return new float[]{
                    1f, 0f
            };
        }
    }

    private static class StubModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            throw new UnsupportedOperationException("Not used in factory tests");
        }
    }

    @SuppressWarnings("java:S2201")
    private static void expectIllegalArgument(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }

    @Test
    void answerRelevanceFactoryCreatesMetric() {
        assertInstanceOf(OutputRelevanceMetric.class,
                         Metrics.answerRelevance(new StubModel()));
        assertInstanceOf(OutputRelevanceMetric.class,
                         Metrics.answerRelevance(new StubModel(),
                                                 OutputRelevanceMetric.DEFAULT_PROMPT_TEMPLATE));
    }

    @Test
    @SuppressWarnings("java:S2201")
    void factoryMethodsPreserveConstructorValidation() {
        expectIllegalArgument(() -> Metrics.answerRelevance(null));
        expectIllegalArgument(() -> Metrics.answerRelevance(new StubModel(), " "));
        expectIllegalArgument(() -> Metrics.outputSimilarity(new StubEmbeddingModel(), ""));
        expectIllegalArgument(() -> Metrics.outputRelevanceBySimilarity(null));
    }

    @Test
    void outputRelevanceBySimilarityFactoryCreatesMetric() {
        assertInstanceOf(OutputRelevanceBySimilarityMetric.class,
                         Metrics.outputRelevanceBySimilarity(new StubEmbeddingModel()));
    }

    @Test
    void outputSimilarityFactoryCreatesMetric() {
        assertInstanceOf(OutputSimilarityMetric.class,
                         Metrics.outputSimilarity(new StubEmbeddingModel(),
                                                  "reference"));
    }
}
