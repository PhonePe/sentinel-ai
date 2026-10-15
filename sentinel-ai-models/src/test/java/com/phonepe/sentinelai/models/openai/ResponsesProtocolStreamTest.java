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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.wire.SseEvent;
import com.phonepe.sentinelai.models.wire.WireContext;
import com.phonepe.sentinelai.models.wire.WireResponse;
import com.phonepe.sentinelai.models.wire.WireStreamEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesProtocolStreamTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final ResponsesProtocol protocol = new ResponsesProtocol();

    @Test
    void argumentsDeltaCarriesFrameOutputIndex() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.function_call_arguments.delta",
                                                               """
                                                                       {"delta": "{\\"command\\": \\"git log -1\\"}",
                                                                        "item_id": "item-1",
                                                                        "output_index": 1,
                                                                        "sequence_number": 5,
                                                                        "type": "response.function_call_arguments.delta"}"""));

        assertEquals(1, events.size());
        final var delta = (WireStreamEvent.ToolCallDelta) events.get(0);
        assertEquals(1, delta.getIndex());
        assertEquals("{\"command\": \"git log -1\"}", delta.getArgumentsFragment());
    }

    @Test
    void blockingResponseDecodesCompletedMessageOutput() throws Exception {
        final var response = protocol.decodeResponse(context(),
                                                     mapper.readTree(fixture("/wiremock/resp-notools.1.json")));

        assertEquals(WireResponse.FinishReasons.STOP, response.getFinishReason());
        assertEquals("{\"output\": {\"message\": \"Hello, Santanu! How can I assist you today?\", "
                + "\"username\": \"Santanu\"}}",
                     response.getContent());
        assertTrue(response.getToolCalls().isEmpty());
        final var usage = response.getUsage();
        assertEquals(284, usage.getInputTokens());
        assertEquals(31, usage.getOutputTokens());
        assertEquals(315, usage.getTotalTokens());
        assertEquals(0, usage.getInputCachedTokens());
        assertEquals(0, usage.getOutputReasoningTokens());
    }

    @Test
    void blockingResponseDecodesRefusalPart() throws Exception {
        final var body = mapper.readTree("""
                {"output": [
                    {"type": "message",
                     "content": [
                        {"type": "output_text", "text": "partial"},
                        {"type": "refusal", "refusal": "cannot help with that"}
                     ]}
                 ],
                 "status": "completed"}""");

        final var response = protocol.decodeResponse(context(), body);

        assertEquals("partial", response.getContent());
        assertEquals("cannot help with that", response.getRefusal());
        assertEquals(WireResponse.FinishReasons.STOP, response.getFinishReason());
    }

    @Test
    void blockingResponseDecodesToolCalls() throws Exception {
        final var response = protocol.decodeResponse(context(),
                                                     mapper.readTree(fixture("/wiremock/resp-tool-output.1.json")));

        assertEquals(WireResponse.FinishReasons.TOOL_CALLS, response.getFinishReason());
        assertNull(response.getContent());
        assertEquals(1, response.getToolCalls().size());
        final var call = response.getToolCalls().get(0);
        assertEquals("call_PqPR7R6ueMHA71WNOnsMHDOQ", call.getId());
        assertEquals("simple_agent_get_name", call.getName());
        assertEquals("{}", call.getArgumentsJson());
    }


    @Test
    void blockingResponseMapsErrorToRefused() throws Exception {
        final var body = mapper.createObjectNode();
        body.put("status", "failed");
        body.set("error", mapper.readTree("{\"code\":\"server_error\"}"));
        body.set("output", mapper.createArrayNode());

        final var response = protocol.decodeResponse(context(), body);

        assertEquals(WireResponse.FinishReasons.REFUSED, response.getFinishReason());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    void blockingResponseMapsIncompleteToLength() throws Exception {
        final var body = mapper.createObjectNode();
        body.put("status", "incomplete");
        body.set("output", mapper.createArrayNode());

        final var response = protocol.decodeResponse(context(), body);

        assertEquals(WireResponse.FinishReasons.LENGTH, response.getFinishReason());
        assertNull(response.getContent());
    }

    @Test
    void blockingResponseWithoutOutputThrows() {
        final var body = mapper.createObjectNode();

        assertThrows(IllegalStateException.class, () -> protocol.decodeResponse(context(), body));
    }

    @Test
    void emptyFrameIsIgnored() {
        assertTrue(protocol.decodeStreamEvent(context(), sseEvent(null, "null")).isEmpty());
    }

    @Test
    void failedFrameWithErrorObjectEndsStream() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.failed",
                                                               "{\"response\":{\"status\":\"failed\",\"error\":{\"code\":\"server_error\"}}}"));
        assertEquals(1, events.size());
        assertEquals("failed", ((WireStreamEvent.StreamFinishEvent) events.get(0)).getFinishReason());
    }

    @Test
    void incompleteFrameWithoutStatusDoesNotThrow() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.incomplete", "{\"response\":{}}"));
        assertEquals(1, events.size());
        assertEquals(com.phonepe.sentinelai.models.wire.WireResponse.FinishReasons.LENGTH,
                     ((WireStreamEvent.StreamFinishEvent) events.get(0)).getFinishReason());
    }

    @Test
    void multipleReasoningItemsAreAllCapturedVerbatim() throws Exception {
        final var body = mapper.readTree("""
                {"output": [
                    {"type": "reasoning", "id": "rs_1", "summary": [{"type": "summary_text", "text": "First. "}]},
                    {"type": "reasoning", "id": "rs_2", "summary": [{"type": "summary_text", "text": "Second."}]},
                    {"type": "message", "content": [{"type": "output_text", "text": "done"}]}
                 ],
                 "status": "completed"}
                """);

        final var response = protocol.decodeResponse(context(), body);

        assertEquals(2, response.getReasoningItems().size());
        assertEquals("rs_1", response.getReasoningItems().get(0).get("id").asText());
        assertEquals("rs_2", response.getReasoningItems().get(1).get("id").asText());
        assertEquals("First. Second.", response.getReasoningContent());
    }

    @Test
    void outputItemAddedCarriesFrameOutputIndex() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.output_item.added",
                                                               """
                                                                       {"item": {"arguments": "",
                                                                         "call_id": "call-1",
                                                                         "name": "core_tool_box_bash",
                                                                         "status": "in_progress",
                                                                         "type": "function_call"},
                                                                        "output_index": 1,
                                                                        "sequence_number": 4,
                                                                        "type": "response.output_item.added"}"""));

        assertEquals(1, events.size());
        final var delta = (WireStreamEvent.ToolCallDelta) events.get(0);
        assertEquals(1, delta.getIndex());
        assertEquals("call-1", delta.getId());
        assertEquals("core_tool_box_bash", delta.getName());
    }

    @Test
    void outputItemDoneEmitsCompleteToolCall() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.output_item.done",
                                                               """
                                                                       {"item": {"arguments": "{\\"command\\": \\"git log -1\\"}",
                                                                         "call_id": "call-1",
                                                                         "name": "core_tool_box_bash",
                                                                         "status": "completed",
                                                                         "type": "function_call"},
                                                                        "output_index": 1,
                                                                        "sequence_number": 9,
                                                                        "type": "response.output_item.done"}"""));

        assertEquals(1, events.size());
        final var complete = (WireStreamEvent.ToolCallComplete) events.get(0);
        assertEquals(1, complete.getIndex());
        assertEquals("call-1", complete.getId());
        assertEquals("core_tool_box_bash", complete.getName());
        assertEquals("{\"command\": \"git log -1\"}", complete.getArguments());
    }

    @Test
    void outputItemDoneIgnoresNonFunctionCallItems() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.output_item.done",
                                                               """
                                                                       {"item": {"content": [],
                                                                         "id": "item-1",
                                                                         "summary": [],
                                                                         "type": "reasoning"},
                                                                        "output_index": 0,
                                                                        "sequence_number": 3,
                                                                        "type": "response.output_item.done"}"""));

        assertTrue(events.isEmpty());
    }

    @Test
    void reasoningDeltaDecodesAsReasoningNotContent() {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.reasoning.delta",
                                                               """
                                                                       {"delta": "Thinking about which tool to use",
                                                                        "output_index": 0,
                                                                        "sequence_number": 2,
                                                                        "type": "response.reasoning.delta"}"""));

        assertEquals(1, events.size());
        final var reasoning = (WireStreamEvent.ReasoningDelta) events.get(0);
        assertEquals("Thinking about which tool to use", reasoning.getContent());
    }

    @Test
    void reasoningItemIsCapturedVerbatimWithEncryptedContent() throws Exception {
        final var body = mapper.readTree("""
                {"output": [
                    {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABoNDg7",
                     "summary": [{"type": "summary_text", "text": "Thinking about the request."}]},
                    {"type": "message",
                     "content": [{"type": "output_text", "text": "done"}]}
                 ],
                 "status": "completed"}
                """);

        final var response = protocol.decodeResponse(context(), body);

        assertEquals(WireResponse.FinishReasons.STOP, response.getFinishReason());
        assertEquals("Thinking about the request.", response.getReasoningContent());
        assertEquals(1, response.getReasoningItems().size());
        final var raw = response.getReasoningItems().get(0);
        assertEquals("rs_1", raw.get("id").asText());
        assertEquals("gAAAAABoNDg7", raw.get("encrypted_content").asText());
    }

    @Test
    void reasoningMarkerExpandsIntoPrecedingInputItemWhenReplayOn() throws Exception {
        final var assistantItem = mapper.readTree("""
                {"type": "function_call", "call_id": "call_1", "name": "test_agent_get_name", "arguments": "{}",
                 "_reasoning": {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABoNDg7"}}
                """);
        final var ctx = context().toBuilder()
                .reasoningReplay(com.phonepe.sentinelai.models.ModelOptions.ReasoningReplay.ON)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of(assistantItem));

        final var input = body.get("input");
        assertEquals(2, input.size());
        final var reasoning = input.get(0);
        assertEquals("reasoning", reasoning.get("type").asText());
        assertEquals("gAAAAABoNDg7", reasoning.get("encrypted_content").asText());
        assertFalse(reasoning.has("_reasoning"));
        final var call = input.get(1);
        assertEquals("function_call", call.get("type").asText());
        assertFalse(call.has("_reasoning"));
    }

    @Test
    void reasoningMarkerIsStrippedWhenReplayOff() throws Exception {
        final var assistantItem = mapper.readTree("""
                {"type": "function_call", "call_id": "call_1", "name": "test_agent_get_name", "arguments": "{}",
                 "_reasoning": {"type": "reasoning", "id": "rs_1", "encrypted_content": "gAAAAABoNDg7"}}
                """);

        final var body = protocol.buildRequestBody(context(), List.of(assistantItem));

        final var input = body.get("input");
        assertEquals(1, input.size());
        assertFalse(input.get(0).has("_reasoning"));
    }

    @Test
    void responseCompletedFrameCarriesReasoningItemsOnFinishEvent() throws Exception {
        final var events = protocol.decodeStreamEvent(context(),
                                                      sseEvent("response.completed",
                                                               """
                                                                       {"response": {"id": "resp_r", "status": "completed",
                                                                        "output": [
                                                                            {"type": "reasoning", "id": "rs_1",
                                                                             "summary": [{"type": "summary_text", "text": "Thinking."}]},
                                                                            {"type": "message",
                                                                             "content": [{"type": "output_text", "text": "done"}]}
                                                                        ]}}
                                                                       """));

        assertEquals(1, events.size());
        final var finish = (WireStreamEvent.StreamFinishEvent) events.get(0);
        assertEquals(WireResponse.FinishReasons.STOP, finish.getFinishReason());
        assertEquals(1, finish.getReasoningItems().size());
        assertEquals("rs_1", finish.getReasoningItems().get(0).get("id").asText());
    }

    private WireContext context() {
        return WireContext.builder()
                .mapper(mapper)
                .modelName("gpt-6-sol")
                .baseUrl("https://example.invalid")
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .tools(Map.of())
                .outputDefinitions(List.of())
                .build();
    }

    private String fixture(final String resource) throws Exception {
        return Files.readString(Path.of(getClass().getResource(resource).toURI()));
    }

    private SseEvent sseEvent(final String event, final String json) {
        return new SseEvent(event, json);
    }
}
