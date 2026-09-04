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

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.AutoCompactionSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessContext;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.Getter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the optional model and model settings selection in {@link AutoCompactionProcessor}.
 */
class AutoCompactionProcessorTest {

    private static final int SESSION_WINDOW = 100_000;

    /**
     * Model that records the setup it was called with and returns a fixed summary.
     */
    @Getter
    private static final class RecordingModel implements Model {
        private final int estimatedTokens;
        private ModelRunContext context;

        private RecordingModel(int estimatedTokens) {
            this.estimatedTokens = estimatedTokens;
        }

        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> preProcessors) {
            this.context = context;
            return CompletableFuture.completedFuture(ModelOutput.success(summary(),
                                                                         List.of(),
                                                                         List.of(),
                                                                         new ModelUsageStats()));
        }

        @Override
        public int estimateTokenCount(List<AgentMessage> messages, AgentSetup agentSetup) {
            return estimatedTokens;
        }
    }

    static Stream<Arguments> selections() {
        return Stream.of(Arguments.of("no compaction model", null, true),
                         Arguments.of("bigger compaction window", settings(SESSION_WINDOW * 2), false),
                         Arguments.of("equal compaction window", settings(SESSION_WINDOW), false),
                         Arguments.of("smaller compaction window", settings(SESSION_WINDOW / 2), true),
                         Arguments.of("no compaction settings", null, false));
    }

    private static AgentMessagesPreProcessContext context(AgentSetup setup) {
        return AgentMessagesPreProcessContext.builder()
                .modelRunContext(new ModelRunContext("agent",
                                                     "run-1",
                                                     "session-1",
                                                     "user-1",
                                                     setup,
                                                     new ModelUsageStats(),
                                                     ProcessingMode.DIRECT))
                .build();
    }

    private static List<AgentMessage> messages() {
        return List.of(new UserPrompt("session-1", "run-1", "hi", false, null));
    }

    private static AutoCompactionProcessor processor(Model model, ModelSettings modelSettings, int threshold) {
        return new AutoCompactionProcessor(AutoCompactionSetup.builder()
                .model(model)
                .modelSettings(modelSettings)
                .compactionTriggerThresholdPercentage(threshold)
                .build());
    }

    private static AgentSetup sessionSetup(Model model) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(model)
                .modelSettings(settings(SESSION_WINDOW))
                .eventBus(new EventBus())
                .build();
    }

    private static ModelSettings settings(int contextWindowSize) {
        return ModelSettings.builder()
                .modelAttributes(ModelAttributes.builder().contextWindowSize(contextWindowSize).build())
                .build();
    }

    private static JsonNode summary() {
        final var mapper = JsonUtils.createMapper();
        final var summary = mapper.createObjectNode();
        summary.put("title", "a title");
        summary.put("summary", "a summary");
        summary.putArray("keywords").add("k1");
        return mapper.createObjectNode().set("sessionOutput", summary);
    }

    @Test
    void compactionAddsSummaryPrompt() {
        final var sessionModel = new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);

        final var result = processor(null, null, 0).process(context(setup), messages(), List.of());

        final var newMessages = result.getNewMessages();
        assertEquals(1, newMessages.size());
        final var userPrompt = (UserPrompt) newMessages.get(0);
        assertTrue(userPrompt.isCompacted());
        assertTrue(userPrompt.getContent().contains("a summary"));
        assertSame(sessionModel, setup.getModel());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selections")
    void modelSelection(String name, ModelSettings compactionSettings, boolean sessionModelExpected) {
        final var sessionModel = new RecordingModel(0);
        final var compactionModel = name.equals("no compaction model") ? null : new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);

        final var result = processor(compactionModel, compactionSettings, 0).process(context(setup),
                                                                                     messages(),
                                                                                     List.of());

        final var usedModel = sessionModelExpected ? sessionModel : compactionModel;
        assertNotNull(usedModel.getContext(),
                      "Expected " + (sessionModelExpected ? "session" : "compaction")
                              + " model to run the compaction");
        if (!sessionModelExpected) {
            assertNull(sessionModel.getContext());
        }
        assertNotNull(result.getTransformedMessages());
    }

    @Test
    void noCompactionBelowThreshold() {
        final var sessionModel = new RecordingModel(1);
        final var compactionModel = new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);

        final var result = processor(compactionModel, settings(SESSION_WINDOW), 50).process(context(setup),
                                                                                            messages(),
                                                                                            List.of());

        assertNull(compactionModel.getContext());
        assertNull(result.getTransformedMessages());
    }

    @Test
    void noCompactionOnPendingToolCall() {
        final var sessionModel = new RecordingModel(0);
        final var compactionModel = new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);
        final List<AgentMessage> messages = List.of(ToolCall.builder()
                .sessionId("session-1")
                .toolCallId("call-1")
                .toolName("tool")
                .build());

        final var result = processor(compactionModel, settings(SESSION_WINDOW), 0).process(context(setup),
                                                                                           messages,
                                                                                           List.of());

        assertNull(compactionModel.getContext());
        assertNull(result.getTransformedMessages());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selections")
    void settingsSelection(String name, ModelSettings compactionSettings, boolean sessionModelExpected) {
        final var sessionModel = new RecordingModel(0);
        final var compactionModel = name.equals("no compaction model") ? null : new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);

        processor(compactionModel, compactionSettings, 0).process(context(setup), messages(), List.of());

        final var usedModel = sessionModelExpected ? sessionModel : compactionModel;
        final var usedAttributes = usedModel.getContext().getAgentSetup().getModelSettings().getModelAttributes();
        final var expectedWindow = sessionModelExpected || compactionSettings == null
                ? SESSION_WINDOW
                : compactionSettings.getModelAttributes().getContextWindowSize();
        assertEquals(expectedWindow, usedAttributes.getContextWindowSize());
    }

    @Test
    void triggerUsesSessionModel() {
        final var sessionModel = new RecordingModel(SESSION_WINDOW);
        final var compactionModel = new RecordingModel(0);
        final var setup = sessionSetup(sessionModel);

        final var result = processor(compactionModel, settings(SESSION_WINDOW), 50).process(context(setup),
                                                                                            messages(),
                                                                                            List.of());

        assertNotNull(compactionModel.getContext());
        assertNotNull(result.getTransformedMessages());
    }
}
