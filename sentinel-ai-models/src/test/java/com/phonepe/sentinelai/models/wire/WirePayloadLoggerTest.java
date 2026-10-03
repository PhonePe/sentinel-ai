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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    static Stream<Arguments> loggingScenarios() {
        return Stream.of(Arguments.of("error logs at ON",
                                      WireLoggingMode.ON,
                                      (Consumer<WirePayloadLogger>) logger -> logger.error("test-model",
                                                                                           429,
                                                                                           "{\"error\":\"rate\"}"),
                                      "Error from Model [test-model]",
                                      "status=429"),
                         Arguments.of("request logs at ON",
                                      WireLoggingMode.ON,
                                      (Consumer<WirePayloadLogger>) logger -> logger.request("test-model",
                                                                                             "{\"a\":1}".getBytes(
                                                                                                                  UTF_8)),
                                      "Request to Model [test-model]",
                                      "{\"a\":1}"),
                         Arguments.of("response logs at ON",
                                      WireLoggingMode.ON,
                                      (Consumer<WirePayloadLogger>) logger -> logger.response("test-model",
                                                                                              "{\"b\":2}"),
                                      "Response from Model [test-model]",
                                      "{\"b\":2}"),
                         Arguments.of("stream frame logs at FRAMES",
                                      WireLoggingMode.FRAMES,
                                      (Consumer<WirePayloadLogger>) logger -> logger.streamFrame("test-model",
                                                                                                 new SseEvent("done",
                                                                                                              "{\"c\":3}")),
                                      "Stream Frame [test-model]",
                                      "event=done"),
                         Arguments.of("nothing logs at OFF: request",
                                      WireLoggingMode.OFF,
                                      (Consumer<WirePayloadLogger>) logger -> logger.request("test-model",
                                                                                             "{}".getBytes(UTF_8)),
                                      null,
                                      null),
                         Arguments.of("nothing logs at OFF: response",
                                      WireLoggingMode.OFF,
                                      (Consumer<WirePayloadLogger>) logger -> logger.response("test-model", "{}"),
                                      null,
                                      null),
                         Arguments.of("nothing logs at OFF: error",
                                      WireLoggingMode.OFF,
                                      (Consumer<WirePayloadLogger>) logger -> logger.error("test-model", 500, "{}"),
                                      null,
                                      null),
                         Arguments.of("stream frame does not log at ON",
                                      WireLoggingMode.ON,
                                      (Consumer<WirePayloadLogger>) logger -> logger.streamFrame("test-model",
                                                                                                 new SseEvent(null,
                                                                                                              "{\"c\":3}")),
                                      null,
                                      null));
    }

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("loggingScenarios")
    void testLoggingGate(final String name,
                         final WireLoggingMode mode,
                         final Consumer<WirePayloadLogger> operation,
                         final String expectedEventPrefix,
                         final String expectedFragment) {
        appender.list.clear();
        operation.accept(loggerOf(mode));
        if (expectedEventPrefix == null) {
            assertEquals(0, appender.list.size(), name);
        }
        else {
            final var events = events(expectedEventPrefix);
            assertEquals(1, events.size(), name);
            final var message = events.get(0).getFormattedMessage();
            assertTrue(message.contains(expectedFragment), name + ": " + message);
        }
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

    private List<ILoggingEvent> events(final String prefix) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(prefix))
                .toList();
    }
}
