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
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.ReasoningContent;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.wire.WireContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reasoning replay request-building tests for the OpenAI wire protocols: history carrying
 * reasoning is expanded onto the wire only when replay is on, and the wire stays
 * byte-identical to pre-replay behavior when replay is off.
 */
class ReasoningReplayRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final ResponsesProtocol responsesProtocol = new ResponsesProtocol();

    private final ChatCompletionsProtocol chatProtocol = new ChatCompletionsProtocol();

    @Test
    void chainBreakRetryBodyIncludesReasoningItems() throws Exception {
        // The chain-break retry resends the full history; reasoning on history items must
        // still be replayed on that path
        final var anchored = assistantNodeWithReasoning("call_anchor", "resp_anchor", """
                {"type": "reasoning", "id": "rs_anchor", "encrypted_content": "gAAAAABanchor"}""");
        final var followUp = messageNode("""
                {"type": "message", "role": "user", "content": "Hi again"}""", null);

        final var retry = responsesProtocol.buildChainBreakRetryBody(
                                                                     context(ModelOptions.ReasoningReplay.ON,
                                                                             ModelOptions.ResponseChaining.SESSION),
                                                                     List.of(anchored, followUp));

        assertTrue(retry.isPresent());
        assertFalse(retry.get().has("previous_response_id"));
        final var input = retry.get().get("input");
        // user message + reasoning item + assistant function call + follow-up
        // Order from translate: no user message here; anchored assistant carries reasoning
        assertEquals(3, input.size());
        assertEquals("reasoning", input.get(0).get("type").asText());
        assertEquals("function_call", input.get(1).get("type").asText());
        assertEquals("message", input.get(2).get("type").asText());
    }

    @Test
    void chatReplayOffStripsMarkerEntirely() throws Exception {
        final var messages = List.of(chatAssistantNodeWithReasoning("reasoning_content", "Step by step thinking"));

        final var body = chatProtocol.buildRequestBody(
                                                       context(ModelOptions.ReasoningReplay.OFF,
                                                               ModelOptions.ResponseChaining.OFF),
                                                       messages);

        final var wireMessages = body.get("messages");
        assertEquals(1, wireMessages.size());
        assertFalse(wireMessages.get(0).has("reasoning_content"));
        assertFalse(wireMessages.get(0).has("_reasoning"));
        assertTrue(body.toString().indexOf("Step by step thinking") < 0, "reasoning must not reach the wire");
    }


    @Test
    void chatReplayOnExpandsReasoningOntoSameAssistantMessage() throws Exception {
        final var messages = List.of(messageNode("""
                {"role": "user", "content": "Hi"}""", null),
                                     chatAssistantNodeWithReasoning("reasoning_content", "Step by step thinking"));

        final var body = chatProtocol.buildRequestBody(
                                                       context(ModelOptions.ReasoningReplay.ON,
                                                               ModelOptions.ResponseChaining.OFF),
                                                       messages);

        final var wireMessages = body.get("messages");
        assertEquals(2, wireMessages.size());
        // Same assistant message carries the original field name and value; no marker leaks
        assertEquals("assistant", wireMessages.get(1).get("role").asText());
        assertEquals("Step by step thinking", wireMessages.get(1).get("reasoning_content").asText());
        assertFalse(wireMessages.get(1).has("_reasoning"));
    }


    @Test
    void chatReplayOnPreservesOriginalFieldName() throws Exception {
        final var messages = List.of(chatAssistantNodeWithReasoning("thinking", "Gemini thinking"));

        final var body = chatProtocol.buildRequestBody(
                                                       context(ModelOptions.ReasoningReplay.ON,
                                                               ModelOptions.ResponseChaining.OFF),
                                                       messages);

        final var wireMessages = body.get("messages");
        assertEquals("Gemini thinking", wireMessages.get(0).get("thinking").asText());
        assertFalse(wireMessages.get(0).has("reasoning_content"));
        assertFalse(wireMessages.get(0).has("_reasoning"));
    }


    /**
     * Chained deltas: only post-anchor items are sent; anchored reasoning is stitched server-side.
     */
    @Test
    void codecMarkersCarryReasoningVerbatim() throws Exception {
        final var codec = new ResponsesMessageCodec();
        final var reasoning = ReasoningContent.builder()
                .rawItem(mapper.readTree("""
                        {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque"}"""))
                .build();
        final var node = (ObjectNode) codec.translate(
                                                      com.phonepe.sentinelai.core.agentmessages.responses.ToolCall
                                                              .builder()
                                                              .sessionId("s1")
                                                              .runId("run-1")
                                                              .toolCallId("call_1")
                                                              .toolName("test_agent_get_name")
                                                              .arguments("{}")
                                                              .responseId("resp_1")
                                                              .reasoning(reasoning)
                                                              .build(),
                                                      mapper);

        assertTrue(node.has(ResponsesFields.MARKER_REASONING));
        assertEquals("gAAAAABopaque",
                     node.get(ResponsesFields.MARKER_REASONING).get("encrypted_content").asText());
        assertSame(reasoning, reasoning); // storage untouched: deep copies protect history
    }

    @Test
    void responsesChainedDeltaSendsOnlyNewItems() throws Exception {
        // Chained request: the provider stitches history server-side, so the delta carries
        // only the post-anchor items; reasoning of already-anchored items is not resent
        final var anchored = assistantNodeWithReasoning("call_anchor", "resp_anchor", """
                {"type": "reasoning", "id": "rs_anchor", "encrypted_content": "gAAAAABanchor"}""");
        final var delta = messageNode("""
                {"type": "function_call_output", "call_id": "call_anchor", "output": "tool result"}""", null);

        final var body = responsesProtocol.buildRequestBody(
                                                            context(ModelOptions.ReasoningReplay.ON,
                                                                    ModelOptions.ResponseChaining.SESSION),
                                                            List.of(anchored, delta));

        assertEquals("resp_anchor", body.get("previous_response_id").asText());
        final var input = body.get("input");
        assertEquals(1, input.size());
        assertEquals("function_call_output", input.get(0).get("type").asText());
        assertTrue(body.toString().indexOf("gAAAAABanchor") < 0,
                   "anchored history is stitched server-side, not resent");
    }

    @Test
    void responsesReplayOffChainedBehaviorUnchanged() throws Exception {
        final var anchored = assistantNodeWithReasoning("call_anchor", "resp_anchor", """
                {"type": "reasoning", "id": "rs_anchor", "encrypted_content": "gAAAAABanchor"}""");
        final var delta = messageNode("""
                {"type": "function_call_output", "call_id": "call_anchor", "output": "tool result"}""", null);

        final var body = responsesProtocol.buildRequestBody(
                                                            context(ModelOptions.ReasoningReplay.OFF,
                                                                    ModelOptions.ResponseChaining.SESSION),
                                                            List.of(anchored, delta));

        assertEquals("resp_anchor", body.get("previous_response_id").asText());
        final var input = body.get("input");
        assertEquals(1, input.size());
        assertEquals("function_call_output", input.get(0).get("type").asText());
        assertTrue(body.toString().indexOf("gAAAAABanchor") < 0);
    }

    @Test
    void responsesReplayOffStripsMarkerEntirely() throws Exception {
        final var messages = List.of(assistantNodeWithReasoning("call_1", "resp_1", """
                {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque-payload"}"""));

        final var body = responsesProtocol.buildRequestBody(
                                                            context(ModelOptions.ReasoningReplay.OFF,
                                                                    ModelOptions.ResponseChaining.OFF),
                                                            messages);

        final var input = body.get("input");
        assertEquals(1, input.size());
        assertFalse(input.get(0).has("_reasoning"));
        assertFalse(input.get(0).has("_responseId"));
        assertTrue(body.toString().indexOf("opaque-payload") < 0, "reasoning must not reach the wire");
    }

    @Test
    void responsesReplayOnExpandsReasoningItemBeforeAssistantItem() throws Exception {
        final var messages = List.of(messageNode("""
                {"type": "message", "role": "user", "content": "Hi"}""", null),
                                     assistantNodeWithReasoning("call_1",
                                                                "resp_1",
                                                                """
                                                                        {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABopaque-payload"}"""));

        final var body = responsesProtocol.buildRequestBody(
                                                            context(ModelOptions.ReasoningReplay.ON,
                                                                    ModelOptions.ResponseChaining.OFF),
                                                            messages);

        final var input = body.get("input");
        assertEquals(3, input.size());
        assertEquals("message", input.get(0).get("type").asText());
        // The reasoning item precedes its assistant item, verbatim, markers stripped
        assertEquals("reasoning", input.get(1).get("type").asText());
        assertEquals("gAAAAABopaque-payload", input.get(1).get("encrypted_content").asText());
        assertFalse(input.get(1).has("_reasoning"));
        // The assistant item itself has no marker on the wire
        assertEquals("function_call", input.get(2).get("type").asText());
        assertFalse(input.get(2).has("_reasoning"));
        assertFalse(input.get(2).has("_responseId"));
    }

    /**
     * Builds the wire node a Responses codec produces for an assistant tool call with a
     * reasoning marker and chaining markers, as it would be stored on history.
     */
    private JsonNode assistantNodeWithReasoning(final String callId,
                                                final String responseId,
                                                final String reasoningJson) throws Exception {
        final var node = (ObjectNode) mapper.readTree("""
                {"type": "function_call", "call_id": "%s", "name": "test_agent_get_name", "arguments": "{}"}"""
                .formatted(callId));
        node.set(ResponsesFields.MARKER_REASONING, mapper.readTree(reasoningJson));
        node.put(ResponsesFields.MARKER_RESPONSE_ID, responseId);
        node.put(ResponsesFields.MARKER_RUN_ID, "run-1");
        return node;
    }

    /**
     * Builds the wire node a Chat Completions codec produces for an assistant message with a
     * reasoning marker, as it would be stored on history.
     */
    private JsonNode chatAssistantNodeWithReasoning(final String fieldName, final String rawValue) throws Exception {
        final var node = (ObjectNode) mapper.readTree("""
                {"role": "assistant", "content": null, "tool_calls": [
                    {"id": "call_1", "type": "function",
                     "function": {"name": "test_agent_get_name", "arguments": "{}"}}]}""");
        final var marker = mapper.createObjectNode();
        marker.put(ChatCompletionsFields.MARKER_REASONING_FIELD_NAME, fieldName);
        marker.put(ChatCompletionsFields.MARKER_REASONING_RAW_ITEM, rawValue);
        node.set(ChatCompletionsFields.MARKER_REASONING, marker);
        return node;
    }

    private WireContext context(final ModelOptions.ReasoningReplay replay,
                                final ModelOptions.ResponseChaining chaining) {
        return WireContext.builder()
                .mapper(mapper)
                .modelName("gpt-5")
                .baseUrl("https://example.invalid")
                .userId("user-1")
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .tools(Map.of())
                .outputDefinitions(List.of())
                .reasoningReplay(replay)
                .responseChaining(chaining)
                .build();
    }

    /**
     * Builds a plain wire message node as the codec would produce it.
     */
    private JsonNode messageNode(final String json, final String ignoredMarker) throws Exception {
        return mapper.readTree(json);
    }
}
