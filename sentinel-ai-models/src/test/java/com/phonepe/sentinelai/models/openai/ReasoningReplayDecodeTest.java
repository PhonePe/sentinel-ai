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

package com.phonepe.sentinelai.models.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.wire.WireContext;
import com.phonepe.sentinelai.models.wire.WireResponse;
import com.phonepe.sentinelai.models.wire.WireStreamEvent;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reasoning capture tests for the OpenAI wire protocols. Responses reasoning output items are
 * captured verbatim (including encrypted payloads) with the summary normalized to text;
 * Chat Completions reasoning fields are captured across provider field-name variants,
 * ported from the sai {@code ReasoningNormalizationInterceptor} matrix.
 */
class ReasoningReplayDecodeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final ResponsesProtocol responsesProtocol = new ResponsesProtocol();

    private final ChatCompletionsProtocol chatProtocol = new ChatCompletionsProtocol();

    private static JsonNode reasoningOf(final WireResponse response) {
        return response.getReasoningItems().isEmpty() ? null : response.getReasoningItems().get(0);
    }

    @Test
    void chatCapturedRawNodeIsTheOriginalFieldNode() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning": {"text": "Object reasoning is kept verbatim"},
                                          "content": "Answer."},
                              "finish_reason": "stop"}]}""");

        // The whole reasoning object (not just the text) is the raw item so replay re-emits
        // the exact provider shape
        final var raw = reasoningOf(response);
        assertTrue(raw != null && raw.isObject());
        assertEquals("Object reasoning is kept verbatim", raw.get("text").asText());
        assertSame(response.getReasoningItems().get(0), raw);
    }

    @Test
    void chatDecodesReasoningContentField() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning_content": "Let me analyse this step by step.",
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("Let me analyse this step by step.", response.getReasoningContent());
        assertEquals("reasoning_content", response.getReasoningFieldName());
        assertEquals(1, response.getReasoningItems().size());
        assertEquals("Let me analyse this step by step.",
                     response.getReasoningItems().get(0).asText());
    }

    @Test
    void chatDecodesReasoningObjectWithContentArray() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning": {"content": [
                                              {"type": "summary", "summary": "First part. "},
                                              {"type": "summary", "summary": "Second part."}
                                          ]},
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("First part. Second part.", response.getReasoningContent());
        assertEquals("reasoning", response.getReasoningFieldName());
    }

    @Test
    void chatDecodesReasoningObjectWithTextField() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning": {"text": "Direct text reasoning."},
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("Direct text reasoning.", response.getReasoningContent());
        assertEquals("reasoning", response.getReasoningFieldName());
    }

    @Test
    void chatDecodesReasoningString() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning": "String reasoning",
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("String reasoning", response.getReasoningContent());
        assertEquals("reasoning", response.getReasoningFieldName());
    }

    @Test
    void chatDecodesThinkingContentField() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "thinking_content": "Anthropic-style thinking",
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("Anthropic-style thinking", response.getReasoningContent());
        assertEquals("thinking_content", response.getReasoningFieldName());
    }

    @Test
    void chatDecodesThinkingField() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "thinking": "Gemini-style thinking",
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("Gemini-style thinking", response.getReasoningContent());
        assertEquals("thinking", response.getReasoningFieldName());
    }

    @Test
    void chatReasoningContentWinsOverAlternates() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant",
                                          "reasoning_content": "Canonical field wins",
                                          "thinking": "Alternate must be ignored",
                                          "content": "The answer is 42."},
                              "finish_reason": "stop"}]}""");

        assertEquals("Canonical field wins", response.getReasoningContent());
        assertEquals("reasoning_content", response.getReasoningFieldName());
    }

    @Test
    void chatStreamDeltaReasoningIsDecodedAsReasoning() {
        final var events = chatProtocol.decodeStreamEvent(chatContext(),
                                                          new com.phonepe.sentinelai.models.wire.SseEvent(
                                                                                                          null,
                                                                                                          """
                                                                                                                  {"choices": [{"index": 0,
                                                                                                                                "delta": {"role": "assistant",
                                                                                                                                          "reasoning_content": "Streamed reasoning"}}]}"""));

        assertEquals(1, events.size());
        final var delta = (WireStreamEvent.ReasoningDelta) events.get(0);
        assertEquals("Streamed reasoning", delta.getContent());
    }

    @Test
    void chatWithoutReasoningCapturesNothing() throws Exception {
        final var response = decodeChat("""
                {"id": "chatcmpl-1",
                 "choices": [{"index": 0,
                              "message": {"role": "assistant", "content": "No reasoning here."},
                              "finish_reason": "stop"}]}""");

        assertNull(response.getReasoningContent());
        assertNull(response.getReasoningFieldName());
        assertTrue(response.getReasoningItems().isEmpty());
    }

    @Test
    void responsesDecodeCapturesReasoningItemVerbatim() throws Exception {
        final var body = mapper.readTree("""
                {"id": "resp_1",
                 "status": "completed",
                 "output": [
                    {"type": "reasoning", "id": "rs_1",
                     "summary": [{"type": "summary_text", "text": "Thinking about the tool"}],
                     "encrypted_content": "gAAAAABopaque-payload"},
                    {"type": "message", "role": "assistant", "status": "completed",
                     "content": [{"type": "output_text", "text": "The answer is 42."}]}
                 ]}""");

        final var response = responsesProtocol.decodeResponse(responsesContext(), body);

        assertEquals(WireResponse.FinishReasons.STOP, response.getFinishReason());
        assertEquals("The answer is 42.", response.getContent());
        assertEquals("Thinking about the tool", response.getReasoningContent());
        // The raw item must be carried verbatim, encrypted payload included, for byte-exact replay
        assertEquals(1, response.getReasoningItems().size());
        final var item = response.getReasoningItems().get(0);
        assertEquals("rs_1", item.get("id").asText());
        assertEquals("gAAAAABopaque-payload", item.get("encrypted_content").asText());
        // Item-based reasoning has no message field name
        assertNull(response.getReasoningFieldName());
    }

    @Test
    void responsesDecodeMergesMultipleReasoningItemSummaries() throws Exception {
        final var body = mapper.readTree("""
                {"id": "resp_1",
                 "status": "completed",
                 "output": [
                    {"type": "reasoning", "summary": [{"type": "summary_text", "text": "First part. "}]},
                    {"type": "reasoning", "summary": [{"type": "summary_text", "text": "Second part."}]},
                    {"type": "message", "role": "assistant", "status": "completed",
                     "content": [{"type": "output_text", "text": "Answer."}]}
                 ]}""");

        final var response = responsesProtocol.decodeResponse(responsesContext(), body);

        assertEquals("First part. Second part.", response.getReasoningContent());
        assertEquals(2, response.getReasoningItems().size());
    }

    @Test
    void responsesDecodeWithoutReasoningCapturesNothing() throws Exception {
        final var response = responsesProtocol.decodeResponse(responsesContext(),
                                                              mapper.readTree("""
                                                                      {"id": "resp_1",
                                                                       "status": "completed",
                                                                       "output": [
                                                                          {"type": "message", "role": "assistant", "status": "completed",
                                                                           "content": [{"type": "output_text", "text": "No reasoning here."}]}
                                                                       ]}"""));

        assertNull(response.getReasoningContent());
        assertTrue(response.getReasoningItems().isEmpty());
        assertNull(response.getReasoningFieldName());
    }

    @Test
    void responsesStreamFinishCarriesRawReasoningItems() throws Exception {
        final var events = responsesProtocol.decodeStreamEvent(responsesContext(),
                                                               new com.phonepe.sentinelai.models.wire.SseEvent(
                                                                                                               "response.completed",
                                                                                                               """
                                                                                                                       {"response": {"id": "resp_1", "status": "completed",
                                                                                                                        "output": [
                                                                                                                           {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque-payload"},
                                                                                                                           {"type": "function_call", "id": "fc_1", "call_id": "call_1",
                                                                                                                            "name": "test_agent_get_name", "arguments": "{}", "status": "completed"}
                                                                                                                        ],
                                                                                                                        "usage": {"input_tokens": 10, "output_tokens": 5, "total_tokens": 15}}}"""));

        assertEquals(1, events.size());
        final var finish = (WireStreamEvent.StreamFinishEvent) events.get(0);
        assertEquals(WireResponse.FinishReasons.TOOL_CALLS, finish.getFinishReason());
        assertEquals(1, finish.getReasoningItems().size());
        assertEquals("gAAAAABopaque-payload",
                     finish.getReasoningItems().get(0).get("encrypted_content").asText());
    }

    @Test
    void responsesStreamReasoningDeltaIsDecodedAsReasoning() {
        final var events = responsesProtocol.decodeStreamEvent(responsesContext(),
                                                               new com.phonepe.sentinelai.models.wire.SseEvent(
                                                                                                               "response.reasoning.delta",
                                                                                                               """
                                                                                                                       {"delta": "Thinking about which tool to use", "output_index": 0}"""));

        assertEquals(1, events.size());
        final var delta = (WireStreamEvent.ReasoningDelta) events.get(0);
        assertEquals("Thinking about which tool to use", delta.getContent());
    }

    @Test
    void responsesStreamSummaryTextDeltaIsDecodedAsReasoning() {
        final var events = responsesProtocol.decodeStreamEvent(responsesContext(),
                                                               new com.phonepe.sentinelai.models.wire.SseEvent(
                                                                                                               "response.reasoning_summary_text.delta",
                                                                                                               """
                                                                                                                       {"delta": "Summary reasoning", "output_index": 0}"""));

        assertEquals(1, events.size());
        final var delta = (WireStreamEvent.ReasoningDelta) events.get(0);
        assertEquals("Summary reasoning", delta.getContent());
    }

    private WireContext chatContext() {
        return WireContext.builder()
                .mapper(mapper)
                .modelName("gpt-4o")
                .baseUrl("https://example.invalid")
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .tools(Map.of())
                .outputDefinitions(List.of())
                .build();
    }

    /**
     * Decodes a Chat Completions response body with the captured reasoning.
     */
    private WireResponse decodeChat(final String json) throws Exception {
        return chatProtocol.decodeResponse(chatContext(), mapper.readTree(json));
    }

    private WireContext responsesContext() {
        return WireContext.builder()
                .mapper(mapper)
                .modelName("gpt-5")
                .baseUrl("https://example.invalid")
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .tools(Map.of())
                .outputDefinitions(List.of())
                .build();
    }
}
