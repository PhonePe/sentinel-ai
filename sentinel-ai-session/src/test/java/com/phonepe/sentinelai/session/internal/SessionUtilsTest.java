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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.session.AgentSessionExtensionSetup;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionUtilsTest {

    private static class TestAgent extends Agent<Object, Object, TestAgent> {
        protected TestAgent(Class<Object> outputType,
                            String systemPrompt,
                            AgentSetup setup,
                            List<com.phonepe.sentinelai.core.agent.AgentExtension<Object, Object, TestAgent>> extensions,
                            Map<String, ExecutableTool> knownTools) {
            super(outputType, systemPrompt, setup, extensions, knownTools);
        }

        @Override
        public String name() {
            return "test";
        }
    }

    private static class TestModel implements Model {
        private final int tokenCount;

        TestModel(int tokenCount) {
            this.tokenCount = tokenCount;
        }

        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return null;
        }

        @Override
        public int estimateTokenCount(List<AgentMessage> messages, AgentSetup agentSetup) {
            return tokenCount;
        }
    }

    @Test
    void testIsAlreadyLengthExceeded_WhenLengthExceeded_ReturnsTrue() {
        var output = AgentOutput.error(
                                       List.of(),
                                       List.of(),
                                       null,
                                       SentinelError.error(ErrorType.LENGTH_EXCEEDED)
        );
        var data = createProcessingCompletedData(output);

        assertTrue(SessionUtils.isAlreadyLengthExceeded(data));
    }

    @Test
    void testIsAlreadyLengthExceeded_WhenOtherError_ReturnsFalse() {
        var output = AgentOutput.error(
                                       List.of(),
                                       List.of(),
                                       null,
                                       SentinelError.error(ErrorType.REFUSED, "dummy reason")
        );
        var data = createProcessingCompletedData(output);

        assertFalse(SessionUtils.isAlreadyLengthExceeded(data));
    }

    @Test
    void testIsAlreadyLengthExceeded_WhenSuccess_ReturnsFalse() {
        var output = AgentOutput.success("data", List.of(), List.of(), null);
        var data = createProcessingCompletedData(output);

        assertFalse(SessionUtils.isAlreadyLengthExceeded(data));
    }

    @Test
    void testIsContextWindowThresholdBreached_WhenThresholdZero_ReturnsTrue() {
        var agentSetup = AgentSetup.builder()
                .build();
        var extensionSetup = AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(0)
                .build();

        assertTrue(SessionUtils.isContextWindowThresholdBreached(List.of(), agentSetup, extensionSetup));
    }

    @Test
    void testIsContextWindowThresholdBreached_WhenTokensAtThresholdBoundary_ReturnsTrue() {
        var model = new TestModel(600);
        var modelSettings = ModelSettings.builder()
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(1000)
                        .build())
                .build();
        var agentSetup = AgentSetup.builder()
                .model(model)
                .modelSettings(modelSettings)
                .build();
        var extensionSetup = AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(60)
                .build();

        assertTrue(SessionUtils.isContextWindowThresholdBreached(List.of(), agentSetup, extensionSetup));
    }

    @Test
    void testIsContextWindowThresholdBreached_WhenTokensBelowThreshold_ReturnsFalse() {
        var model = new TestModel(500);
        var modelSettings = ModelSettings.builder()
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(1000)
                        .build())
                .build();
        var agentSetup = AgentSetup.builder()
                .model(model)
                .modelSettings(modelSettings)
                .build();
        var extensionSetup = AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(60)
                .build();

        assertFalse(SessionUtils.isContextWindowThresholdBreached(List.of(), agentSetup, extensionSetup));
    }

    @Test
    void testIsContextWindowThresholdBreached_WhenTokensExceedThreshold_ReturnsTrue() {
        var model = new TestModel(700);
        var modelSettings = ModelSettings.builder()
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(1000)
                        .build())
                .build();
        var agentSetup = AgentSetup.builder()
                .model(model)
                .modelSettings(modelSettings)
                .build();
        var extensionSetup = AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(60)
                .build();

        assertTrue(SessionUtils.isContextWindowThresholdBreached(List.of(), agentSetup, extensionSetup));
    }

    @Test
    void testIsContextWindowThresholdBreached_WhenTokensJustBelowBoundary_ReturnsFalse() {
        var model = new TestModel(599);
        var modelSettings = ModelSettings.builder()
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(1000)
                        .build())
                .build();
        var agentSetup = AgentSetup.builder()
                .model(model)
                .modelSettings(modelSettings)
                .build();
        var extensionSetup = AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(60)
                .build();

        assertFalse(SessionUtils.isContextWindowThresholdBreached(List.of(), agentSetup, extensionSetup));
    }

    private Agent.ProcessingCompletedData<Object, Object, TestAgent> createProcessingCompletedData(
                                                                                                   AgentOutput<?> output) {
        return new Agent.ProcessingCompletedData<Object, Object, TestAgent>(
                                                                            null,
                                                                            null,
                                                                            null,
                                                                            null,
                                                                            (AgentOutput<Object>) output,
                                                                            ProcessingMode.DIRECT
        );
    }
}
