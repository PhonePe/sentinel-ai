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

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.wire.SseEvent;
import com.phonepe.sentinelai.models.wire.WireContext;
import com.phonepe.sentinelai.models.wire.WireResponse;
import com.phonepe.sentinelai.models.wire.WireStreamEvent;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ChatCompletionsProtocol} request assembly: extras merge order, overrides of
 * protocol-built fields, and default tool choice values.
 */
class ChatCompletionsProtocolTest {

    private final ObjectMapper mapper = JsonUtils.createMapper();

    @Test
    void blockingRequestOmitsStreamFlag() {
        final var protocol = new ChatCompletionsProtocol();

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                   List.of());

        assertFalse(body.has(ChatCompletionsFields.STREAM));
    }

    @Test
    void blockingRequestOmitsStreamOptions() {
        final var protocol = new ChatCompletionsProtocol();

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                   List.of());

        assertFalse(body.has(ChatCompletionsFields.STREAM_OPTIONS));
    }

    @Test
    void classifyErrorMapsRateLimitAndOthers() {
        final var protocol = new ChatCompletionsProtocol();

        assertEquals(ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED,
                     protocol.classifyError(429, mapper.nullNode()));
        assertEquals(ErrorType.MODEL_CALL_HTTP_FAILURE,
                     protocol.classifyError(500, mapper.nullNode()));
    }

    @Test
    void emptyOutputDefinitionsOmitResponseFormat() {
        final var protocol = new ChatCompletionsProtocol();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.set("required", mapper.createArrayNode());
        final var ctx = context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null).toBuilder()
                .outputSchema(schema)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of());

        assertFalse(body.has(ChatCompletionsFields.RESPONSE_FORMAT));
    }

    @Test
    void emptyUserIdIsOmitted() {
        final var protocol = new ChatCompletionsProtocol();
        final var ctx = WireContext.builder()
                .modelName("test-model")
                .baseUrl("http://localhost")
                .userId("")
                .tools(Map.of())
                .outputDefinitions(List.of())
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .mapper(mapper)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of());

        assertFalse(body.has(ChatCompletionsFields.USER));
    }

    @Test
    void endpointAppendsChatCompletionsPath() {
        final var protocol = new ChatCompletionsProtocol();

        assertEquals("http://localhost/chat/completions",
                     protocol.endpoint(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null)));
    }

    @Test
    void extrasDeepMergeIntoNestedObjects() {
        final var protocol = new ChatCompletionsProtocol();
        final var options = optionsWithExtras("""
                {"nested": {"a": 1, "b": {"c": 2}}}
                """);

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, options),
                                                   List.of());

        final var nested = body.get("nested");
        assertEquals(1, nested.get("a").asInt());
        assertEquals(2, nested.get("b").get("c").asInt());
    }

    @Test
    void extrasOverrideProtocolBuiltFields() {
        final var protocol = new ChatCompletionsProtocol();
        final var settings = ModelSettings.builder().temperature(0.1f).topP(0.8f).build();
        final var options = optionsWithExtras("""
                {"temperature": 0.9, "top_p": 0.5}
                """);

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, settings, options),
                                                   List.of());

        assertEquals(0.9, body.get("temperature").asDouble(), 0.000001);
        assertEquals(0.5, body.get("top_p").asDouble(), 0.000001);
    }

    @Test
    void extrasReachTheWireUnchanged() {
        final var protocol = new ChatCompletionsProtocol();
        final var options = optionsWithExtras("""
                {"top_k": 5, "chat_template_kwargs": {"enable_thinking": false}}
                """);

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, options),
                                                   List.of());

        assertEquals(5, body.get("top_k").asInt());
        assertFalse(body.get("chat_template_kwargs").get("enable_thinking").asBoolean());
    }

    @Test
    void finishAndUsageSameFrameKeepsBoth() {
        final var protocol = new ChatCompletionsProtocol();

        final var events = protocol.decodeStreamEvent(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                      sseEvent("""
                                                              {"choices": [{"finish_reason": "stop"}],
                                                               "usage": {"prompt_tokens": 3, "completion_tokens": 4, "total_tokens": 7}}
                                                              """));

        assertEquals(1, events.size());
        final var finish = (WireStreamEvent.StreamFinishEvent) events.get(0);
        assertEquals(WireResponse.FinishReasons.STOP, finish.finishReason());
        assertEquals(3, finish.usage().inputTokens());
        assertEquals(4, finish.usage().outputTokens());
        assertEquals(7, finish.usage().totalTokens());
    }

    @Test
    void frameWithContentToolCallAndFinishKeepsAllEvents() {
        final var protocol = new ChatCompletionsProtocol();

        final var events = protocol.decodeStreamEvent(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                      sseEvent("""
                                                              {"choices": [{"index": 0, "delta": {"content": "partial", "tool_calls": [{"index": 0, "id": "call-1", "function": {"name": "read_file"}}]}, "finish_reason": "tool_calls"}]}"""));

        assertEquals(3, events.size());
        assertEquals(WireStreamEvent.ContentDelta.class, events.get(0).getClass());
        assertEquals("partial", ((WireStreamEvent.ContentDelta) events.get(0)).content());
        assertEquals(WireStreamEvent.ToolCallDelta.class, events.get(1).getClass());
        assertEquals("call-1", ((WireStreamEvent.ToolCallDelta) events.get(1)).id());
        assertEquals("read_file", ((WireStreamEvent.ToolCallDelta) events.get(1)).name());
        assertEquals(WireStreamEvent.StreamFinishEvent.class, events.get(2).getClass());
    }

    @Test
    void frameWithToolCallAndFinishReasonKeepsBothEvents() {
        final var protocol = new ChatCompletionsProtocol();

        final var events = protocol.decodeStreamEvent(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                      sseEvent("""
                                                              {"choices": [{"index": 0, "delta": {"tool_calls": [{"index": 0, "function": {"arguments": "{\\"path\\": \\"README.md\\"}"}}]}, "finish_reason": "tool_calls"}]}"""));

        assertEquals(2, events.size());
        assertEquals(WireStreamEvent.ToolCallDelta.class, events.get(0).getClass());
        final var delta = (WireStreamEvent.ToolCallDelta) events.get(0);
        assertEquals(0, delta.index());
        assertEquals("{\"path\": \"README.md\"}", delta.argumentsFragment());
        assertEquals(WireStreamEvent.StreamFinishEvent.class, events.get(1).getClass());
        assertEquals(WireResponse.FinishReasons.TOOL_CALLS,
                     ((WireStreamEvent.StreamFinishEvent) events.get(1)).finishReason());
    }

    @Test
    void noExtrasLeavesBodyUntouched() {
        final var protocol = new ChatCompletionsProtocol();
        final var settings = ModelSettings.builder().temperature(0.1f).build();

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, settings, null),
                                                   List.of());

        assertEquals(0.1, body.get("temperature").asDouble(), 0.000001);
        assertNull(body.get("top_k"));
    }

    @Test
    void nullSchemaOmitsResponseFormat() {
        final var protocol = new ChatCompletionsProtocol();

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                   List.of());

        assertFalse(body.has(ChatCompletionsFields.RESPONSE_FORMAT));
    }

    @Test
    void requestBodyCarriesCoreFields() {
        final var protocol = new ChatCompletionsProtocol();

        final var body = protocol.buildRequestBody(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                   List.of());

        assertEquals("test-model", body.get(ChatCompletionsFields.MODEL).asText());
        assertEquals(1, body.get(ChatCompletionsFields.N).asInt());
        assertEquals("user-1", body.get(ChatCompletionsFields.USER).asText());
        assertTrue(body.get(ChatCompletionsFields.MESSAGES).isArray());
        assertTrue(body.get(ChatCompletionsFields.MESSAGES).isEmpty());
    }

    @Test
    void schemaOnWireBuildsResponseFormat() {
        final var protocol = new ChatCompletionsProtocol();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties",
                   mapper.createObjectNode().set("answer", mapper.createObjectNode().put("type", "string")));
        final var ctx = context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null).toBuilder()
                .outputSchema(schema)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of());

        assertTrue(body.has(ChatCompletionsFields.RESPONSE_FORMAT));
    }

    @Test
    void streamingRequestCarriesStreamFlag() {
        final var protocol = new ChatCompletionsProtocol();
        final var ctx = context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null).toBuilder()
                .streaming(true)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of());

        assertTrue(body.get(ChatCompletionsFields.STREAM).asBoolean());
    }

    @Test
    void streamingRequestCarriesStreamOptionsIncludeUsage() {
        final var protocol = new ChatCompletionsProtocol();
        final var ctx = context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null).toBuilder()
                .streaming(true)
                .build();

        final var body = protocol.buildRequestBody(ctx, List.of());

        assertTrue(body.get(ChatCompletionsFields.STREAM_OPTIONS)
                .get(ChatCompletionsFields.INCLUDE_USAGE)
                .asBoolean());
    }

    @Test
    void unrelatedFrameYieldsEmptyEventList() {
        final var protocol = new ChatCompletionsProtocol();

        final var events = protocol.decodeStreamEvent(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                      sseEvent("""
                                                              {"choices": [], "created": 0}"""));

        assertTrue(events.isEmpty());
    }

    @Test
    void usageOnlyFrameYieldsUsageEvent() {
        final var protocol = new ChatCompletionsProtocol();

        final var events = protocol.decodeStreamEvent(context(OutputGenerationMode.STRUCTURED_OUTPUT, null, null),
                                                      sseEvent("""
                                                              {"usage": {"prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12}}
                                                              """));

        assertEquals(1, events.size());
        final var usage = ((WireStreamEvent.StreamUsageEvent) events.get(0)).usage();
        assertEquals(5, usage.inputTokens());
        assertEquals(7, usage.outputTokens());
        assertEquals(12, usage.totalTokens());
    }

    private WireContext context(final OutputGenerationMode mode,
                                final ModelSettings settings,
                                final ModelOptions options) {
        return WireContext.builder()
                .modelName("test-model")
                .baseUrl("http://localhost")
                .userId("user-1")
                .modelSettings(settings)
                .tools(Map.of())
                .outputDefinitions(List.of())
                .outputGenerationMode(mode)
                .mapper(mapper)
                .extras(options == null ? null : options.getExtras())
                .build();
    }

    private ModelOptions optionsWithExtras(final String extrasJson) {
        try {
            final var extras = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(extrasJson);
            return ModelOptions.builder().extras(extras).build();
        }
        catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SseEvent sseEvent(final String json) {
        return new SseEvent(null, json);
    }

}
