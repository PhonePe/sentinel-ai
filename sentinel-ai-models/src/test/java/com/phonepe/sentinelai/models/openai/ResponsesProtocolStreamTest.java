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
import com.phonepe.sentinelai.models.wire.WireStreamEvent;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private SseEvent sseEvent(final String event, final String json) {
        return new SseEvent(event, json);
    }
}
