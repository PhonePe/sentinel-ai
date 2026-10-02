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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Logs the wire payloads of one {@code ConfiguredModel}: the exact request JSON sent to the
 * provider, the final response JSON received from it, every raw stream frame at
 * {@link WireLoggingMode#FRAMES}, and the body of every failed call. The output goes to a
 * dedicated logger ({@value #WIRE_LOGGER_NAME}) at INFO level so users can route or silence
 * wire payloads without touching other loggers. The level gates all formatting work; a
 * disabled log call costs one enum comparison.
 */
@RequiredArgsConstructor
public class WirePayloadLogger {

    /**
     * Name of the dedicated wire payload logger.
     */
    public static final String WIRE_LOGGER_NAME = "com.phonepe.sentinelai.models.wire.WIRE";

    private static final Logger WIRE = LoggerFactory.getLogger(WIRE_LOGGER_NAME);

    private final WireLoggingMode mode;

    private static JsonNode argumentsOf(final ObjectMapper mapper, final String argumentsJson) {
        try {
            return mapper.readTree(argumentsJson);
        }
        catch (final Exception e) {
            return mapper.getNodeFactory().textNode(argumentsJson);
        }
    }

    /**
     * Logs the body of a failed model call.
     */
    public void error(final String modelName, final int status, final String body) {
        if (!mode.logsResponses() || !WIRE.isInfoEnabled()) {
            return;
        }
        WIRE.info("Error from Model [{}]: status={} {}", modelName, status, body);
    }

    /**
     * Logs the request body sent to the provider, exactly the bytes sent on the wire.
     */
    public void request(final String modelName, final byte[] body) {
        if (!mode.logsResponses() || !WIRE.isInfoEnabled()) {
            return;
        }
        WIRE.info("Request to Model [{}]: {}", modelName, new String(body, UTF_8));
    }

    /**
     * Logs the final response received from the provider.
     */
    public void response(final String modelName, final String body) {
        if (!mode.logsResponses() || !WIRE.isInfoEnabled()) {
            return;
        }
        WIRE.info("Response from Model [{}]: {}", modelName, body);
    }

    /**
     * Logs one raw stream frame; logs nothing unless the mode is {@link WireLoggingMode#FRAMES}.
     */
    public void streamFrame(final String modelName, final SseEvent event) {
        if (!mode.logsFrames() || !WIRE.isInfoEnabled()) {
            return;
        }
        WIRE.info("Stream Frame [{}]: event={} data={}", modelName, event.getEvent(), event.getData());
    }

    /**
     * Logs the assembled final response of the model in streaming mode.
     */
    public void streamResponse(final String modelName,
                               final WireStreamEvent.StreamFinishEvent finishEvent,
                               final String content,
                               final Iterable<WireToolCall> toolCalls,
                               final ObjectMapper mapper) {
        if (!mode.logsResponses() || !WIRE.isInfoEnabled()) {
            return;
        }
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("finishReason", finishEvent.getFinishReason());
        if (finishEvent.getRefusal() != null) {
            payload.put("refusal", finishEvent.getRefusal());
        }
        payload.put("content", content);
        final var toolCallNodes = mapper.createArrayNode();
        for (final WireToolCall toolCall : toolCalls) {
            final var toolCallNode = mapper.createObjectNode();
            toolCallNode.put("id", toolCall.getId());
            toolCallNode.put("name", toolCall.getName());
            toolCallNode.set("arguments", argumentsOf(mapper, toolCall.getArgumentsJson()));
            toolCallNodes.add(toolCallNode);
        }
        payload.set("toolCalls", toolCallNodes);
        payload.set("usage", mapper.valueToTree(finishEvent.getUsage()));
        WIRE.info("Response from Model [{}]: {}", modelName, payload);
    }

}
