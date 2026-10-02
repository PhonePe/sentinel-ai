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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WirePayloadLogger} through a logback {@link ListAppender} attached to the
 * dedicated WIRE logger.
 */
class WirePayloadLoggerTest {

    private ListAppender<ILoggingEvent> appender;

    private Logger wireLogger;

    private static WirePayloadLogger loggerOf(final WireLoggingMode mode) {
        return new WirePayloadLogger(mode);
    }

    @BeforeEach
    void setUp() {
        synchronized (WirePayloadLoggerTest.class) {
            wireLogger = (Logger) LoggerFactory.getLogger(WirePayloadLogger.WIRE_LOGGER_NAME);
            appender = new ListAppender<>();
            appender.start();
            wireLogger.addAppender(appender);
        }
    }

    @AfterEach
    void tearDown() {
        synchronized (WirePayloadLoggerTest.class) {
            wireLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testAllLogLinesAreInfoLevel() {
        final var logger = loggerOf(WireLoggingMode.FRAMES);
        logger.request("m", "{}".getBytes(UTF_8));
        logger.response("m", "{}");
        logger.streamFrame("m", new SseEvent(null, "{}"));
        logger.error("m", 500, "{}");
        assertEquals(4, appender.list.size());
        assertTrue(appender.list.stream().allMatch(event -> event.getLevel() == Level.INFO));
    }

    @Test
    void testErrorLogsAtOn() {
        loggerOf(WireLoggingMode.ON).error("test-model", 429, "{\"error\":\"rate\"}");
        assertEquals(1, countEvents("Error from Model [test-model]"));
        final var message = events("Error from Model [test-model]").get(0).getFormattedMessage();
        assertTrue(message.contains("status=429"));
        assertTrue(message.contains("{\"error\":\"rate\"}"));
    }

    @Test
    void testNothingLogsAtOff() {
        final var logger = loggerOf(WireLoggingMode.OFF);
        logger.request("test-model", "{}".getBytes(UTF_8));
        logger.response("test-model", "{}");
        logger.streamFrame("test-model", new SseEvent(null, "{}"));
        logger.error("test-model", 500, "{}");
        assertEquals(0, appender.list.size());
    }

    @Test
    void testRequestLogsAtOn() {
        loggerOf(WireLoggingMode.ON).request("test-model", "{\"a\":1}".getBytes(UTF_8));
        final var events = events("Request to Model [test-model]");
        assertEquals(1, events.size());
        assertTrue(events.get(0).getFormattedMessage().contains("{\"a\":1}"));
    }

    @Test
    void testResponseLogsAtOn() {
        loggerOf(WireLoggingMode.ON).response("test-model", "{\"b\":2}");
        final var events = events("Response from Model [test-model]");
        assertEquals(1, events.size());
        assertTrue(events.get(0).getFormattedMessage().contains("{\"b\":2}"));
    }

    @Test
    void testStreamFrameDoesNotLogAtOn() {
        loggerOf(WireLoggingMode.ON).streamFrame("test-model", new SseEvent(null, "{\"c\":3}"));
        assertEquals(0, appender.list.size());
    }

    @Test
    void testStreamFrameLogsAtFrames() {
        loggerOf(WireLoggingMode.FRAMES).streamFrame("test-model", new SseEvent("done", "{\"c\":3}"));
        final var events = events("Stream Frame [test-model]");
        assertEquals(1, events.size());
        final var message = events.get(0).getFormattedMessage();
        assertTrue(message.contains("event=done"));
        assertTrue(message.contains("{\"c\":3}"));
    }

    @Test
    void testStreamResponseLogsAssembledPayload() {
        final var finishEvent = new WireStreamEvent.StreamFinishEvent(WireResponse.FinishReasons.STOP,
                                                                      null,
                                                                      new WireUsage(1, 2, 3, null, null, null, null));
        loggerOf(WireLoggingMode.ON).streamResponse("test-model",
                                                    finishEvent,
                                                    "hello",
                                                    List.of(new WireToolCall("id1", "tool", "{\"x\":1}")),
                                                    new ObjectMapper());
        final var events = events("Response from Model [test-model]");
        assertEquals(1, events.size());
        final var message = events.get(0).getFormattedMessage();
        assertTrue(message.contains("hello"));
        assertTrue(message.contains("tool"));
        assertTrue(message.contains("finishReason"));
    }

    private int countEvents(final String prefix) {
        return events(prefix).size();
    }

    private List<ILoggingEvent> events(final String prefix) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(prefix))
                .toList();
    }
}
