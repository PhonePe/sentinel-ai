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

package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.ReasoningContent;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reasoning-aware token counting: replay OFF keeps the pre-replay counts; replay ON counts the
 * serialized raw reasoning item with the same encoder because replayed reasoning items consume
 * real input tokens on the wire.
 */
class ReasoningTokenCountingTest {

    private static final com.knuddels.jtokkit.api.Encoding ENCODER = com.knuddels.jtokkit.Encodings
            .newDefaultEncodingRegistry()
            .getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);

    private final GenericTokenCounter tokenCounter = new GenericTokenCounter();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Sanity: the helper-based count matches a direct encoding of the reasoning text.
     */
    @Test
    void helperCountMatchesEncodingOfReasoningText() {
        final var reasoningText = "Deep chain of thought";
        final var text = textWithReasoning("I am fine, thank you!",
                                           ReasoningContent.builder().content(reasoningText).build());
        final var onCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build(), null, List.of(text));
        final var offCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(text));
        assertEquals(offCount + countTokens(reasoningText), onCount);
    }

    @Test
    void replayOffIgnoresReasoning() throws Exception {
        final var text = textWithReasoning("I am fine, thank you!", "Deep chain of thought");

        final var offCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(text));

        final Text withoutReasoningText = textWithReasoning("I am fine, thank you!", (ReasoningContent) null);
        final var withoutReasoning = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(withoutReasoningText));

        // Default (no options) must match OFF
        final var defaultCount = tokenCounter.estimateTokenCount(ModelOptions.DEFAULT, null, List.of(text));

        assertEquals(withoutReasoning, offCount, "replay OFF must not count reasoning");
        assertEquals(offCount, defaultCount, "default model options behave like replay OFF");
    }

    @Test
    void replayOnCountsRawReasoningItem() throws Exception {
        final var rawItem = mapper.readTree("""
                {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque-payload"}""");
        final var text = textWithReasoning("I am fine, thank you!",
                                           ReasoningContent.builder()
                                                   .content("Deep chain of thought")
                                                   .rawItem(rawItem)
                                                   .build());

        final var offCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(text));
        final var onCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build(), null, List.of(text));

        // ON must exceed OFF by at least the tokens of the serialized raw item
        assertEquals(offCount
                + countTokens(rawItem.toString()), onCount, "replay ON counts the serialized raw item");
    }

    @Test
    void replayOnCountsReasoningOnToolCall() throws Exception {
        final var rawItem = mapper.readTree("""
                {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque-payload"}""");
        final var toolCall = new ToolCall("s1",
                                          "run-1",
                                          null,
                                          null,
                                          "call_1",
                                          "get_weather",
                                          "{}",
                                          null,
                                          ReasoningContent.builder()
                                                  .rawItem(rawItem)
                                                  .build());

        final var offCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(toolCall));
        final var onCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build(), null, List.of(toolCall));

        assertEquals(offCount + countTokens(rawItem.toString()), onCount);
    }

    @Test
    void replayOnFallsBackToContentWhenNoRawItem() {
        final var text = textWithReasoning("I am fine, thank you!",
                                           ReasoningContent.builder()
                                                   .content("Deep chain of thought")
                                                   .build());

        final var offCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.OFF)
                .build(), null, List.of(text));
        final var onCount = tokenCounter.estimateTokenCount(ModelOptions.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build(), null, List.of(text));

        assertEquals(offCount + countTokens("Deep chain of thought"), onCount);
    }

    private int countTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return ENCODER.encodeOrdinary(content).size();
    }

    /**
     * Builds a text response with optional reasoning.
     */
    private Text textWithReasoning(final String content, final ReasoningContent reasoning) {
        return new Text("s1", "run-1", null, null, content, new ModelUsageStats(), 1L, null, reasoning);
    }

    private Text textWithReasoning(final String content, final String reasoningText) {
        return textWithReasoning(content,
                                 reasoningText == null
                                         ? null
                                         : ReasoningContent.builder().content(reasoningText).build());
    }
}
