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

package com.phonepe.sentinelai.models.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link WireProtocolDecoratorSupport} delegation and hook order, and
 * {@link MessageTransformer#applyAll} chain order.
 */
class WireProtocolDecoratorSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Records every hook invocation order into a shared list so tests assert the exact sequence.
     */
    private static final class RecordingDecorator extends WireProtocolDecoratorSupport {

        private final List<String> log;

        private final WireProtocol delegate;

        RecordingDecorator(final WireProtocol delegate, final List<String> log) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public String endpoint(final WireContext ctx) {
            return delegate().endpoint(ctx);
        }

        @Override
        protected WireProtocol delegate() {
            return delegate;
        }

        @Override
        protected JsonNode transformExtras(final JsonNode extras) {
            log.add("transformExtras");
            return extras;
        }

        @Override
        protected ObjectNode transformRequest(final WireContext ctx, final ObjectNode body) {
            log.add("transformRequest");
            body.put("decorated", true);
            return body;
        }

        @Override
        protected JsonNode transformResponse(final JsonNode body) {
            log.add("transformResponse");
            return body;
        }

        @Override
        protected SseEvent transformStreamEvent(final SseEvent event) {
            log.add("transformStreamEvent");
            return event;
        }
    }

    private static WireContext context(final ObjectNode extras) {
        return WireContext.builder()
                .modelName("gpt-test")
                .baseUrl("http://localhost")
                .tools(Map.of())
                .outputDefinitions(List.of())
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .extras(extras)
                .mapper(MAPPER)
                .build();
    }

    private static UserPrompt text(final String content) {
        return UserPrompt.text("session", "run", content, LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    @Test
    void testApplyAllReturnsSameListWithoutTransformers() {
        final var messages = List.<AgentMessage>of(text("hello"));
        assertSame(messages, MessageTransformer.applyAll(messages, null));
        assertSame(messages, MessageTransformer.applyAll(messages, List.of()));
    }

    @Test
    void testApplyAllRunsTransformersInOrder() {
        final UnaryOperator<AgentMessage> tagFirst = m -> {
            if (m instanceof final UserPrompt prompt) {
                return UserPrompt.text(prompt.getSessionId(),
                                       prompt.getRunId(),
                                       prompt.getContent() + "-first",
                                       prompt.getSentAt());
            }
            return m;
        };
        final UnaryOperator<AgentMessage> tagSecond = m -> {
            if (m instanceof final UserPrompt prompt) {
                return UserPrompt.text(prompt.getSessionId(),
                                       prompt.getRunId(),
                                       prompt.getContent() + "-second",
                                       prompt.getSentAt());
            }
            return m;
        };

        final var result = MessageTransformer.applyAll(List.of(text("hello")),
                                                       List.of(tagFirst, tagSecond));

        assertEquals(1, result.size());
        assertEquals("hello-first-second", ((UserPrompt) result.get(0)).getContent());
    }

    @Test
    void testPureDelegationForNonHookedMethods() {
        final var inner = new TestWireProtocol();
        final var protocol = new RecordingDecorator(inner, new java.util.ArrayList<String>());

        final var ctx = context(null);
        assertEquals(inner.endpoint(ctx), protocol.endpoint(ctx));
        final var message = text("hello");
        assertEquals(inner.messageCodec().translate(message, MAPPER),
                     protocol.messageCodec().translate(message, MAPPER));
        assertEquals(ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED, protocol.classifyError(429, null));
    }

    @Test
    void testTransformExtrasRunsBeforeDelegateMergesExtras() {
        final var log = new java.util.ArrayList<String>();
        final var protocol = new RecordingDecorator(new TestWireProtocol(), log);

        final var body = MAPPER.createObjectNode();
        protocol.applyExtras(body, MAPPER.createObjectNode().put("top_k", 7));

        assertEquals(7, body.get("top_k").asInt());
        assertEquals(List.of("transformExtras"), log);
    }

    @Test
    void testTransformRequestRunsAfterDelegateBuildsBody() {
        final var log = new java.util.ArrayList<String>();
        final var protocol = new RecordingDecorator(new TestWireProtocol(), log);

        final var body = protocol.buildRequestBody(context(null), List.of());

        assertTrue(body.has("model"));
        assertTrue(body.has("messages"));
        assertTrue(body.get("decorated").asBoolean());
        assertEquals(List.of("transformRequest"), log);
    }

    @Test
    void testTransformResponseRunsBeforeDelegateDecodes() {
        final var log = new java.util.ArrayList<String>();
        final var protocol = new RecordingDecorator(new TestWireProtocol(), log);

        final var body = MAPPER.createObjectNode();
        body.putArray("choices").addObject().put("finish_reason", "stop");
        final var choice = (ObjectNode) body.get("choices").get(0);
        choice.putObject("message").put("content", "hi");

        final var response = protocol.decodeResponse(context(null), body);

        assertEquals("hi", response.content());
        assertEquals(WireResponse.FinishReasons.STOP, response.finishReason());
        assertEquals(List.of("transformResponse"), log);
    }

    @Test
    void testTransformStreamEventRunsBeforeDelegateDecodes() {
        final var log = new java.util.ArrayList<String>();
        final var protocol = new RecordingDecorator(new TestWireProtocol(), log);

        final var chunk = MAPPER.createObjectNode();
        final var choice = chunk.putArray("choices").addObject();
        choice.putObject("delta").put("content", "hi");
        final var decoded = protocol.decodeStreamEvent(context(null), new SseEvent(null, chunk.toString()));

        assertEquals(WireStreamEvent.ContentDelta.class, decoded.getClass());
        assertEquals("hi", ((WireStreamEvent.ContentDelta) decoded).content());
        assertEquals(List.of("transformStreamEvent"), log);
    }
}
