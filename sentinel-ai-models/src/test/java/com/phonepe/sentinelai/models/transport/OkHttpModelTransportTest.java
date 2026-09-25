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

package com.phonepe.sentinelai.models.transport;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link OkHttpModelTransport}: blocking execute, SSE parsing and stream error handling.
 */
@WireMockTest
class OkHttpModelTransportTest {

    private static TransportRequest requestFor(final WireMockRuntimeInfo wiremock) {
        return requestFor(wiremock, "{\"model\":\"gpt-4o\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static TransportRequest requestFor(final WireMockRuntimeInfo wiremock, final byte[] body) {
        return new TransportRequest(wiremock.getHttpBaseUrl() + "/chat/completions",
                                    "POST",
                                    Map.of("Content-Type", "application/json"),
                                    body);
    }

    @Test
    void testExecuteErrorStatus(final WireMockRuntimeInfo wiremock) {
        stubFor(post("/chat/completions").willReturn(aResponse().withStatus(429).withBody("rate limited")));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var response = transport.execute(requestFor(wiremock)).join();

        assertFalse(response.isSuccessful());
        assertEquals(429, response.status());
        assertEquals("rate limited", new String(response.body()));
    }

    @Test
    void testExecuteHappyPath(final WireMockRuntimeInfo wiremock) {
        stubFor(post("/chat/completions").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true}")));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var response = transport.execute(requestFor(wiremock)).join();

        assertTrue(response.isSuccessful());
        assertEquals(200, response.status());
        assertEquals("{\"ok\":true}", new String(response.body()));
        assertEquals("application/json", response.headers().get("Content-Type"));
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(containing("gpt-4o")));
    }

    @Test
    void testStreamDataOnlyEvents(final WireMockRuntimeInfo wiremock) {
        final var body = "{\"a\":1}\n\n{\"a\":2}\n\n[DONE]\n\n";
        stubFor(post("/chat/completions").willReturn(okForContentType("text/event-stream", body)));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var events = transport.stream(requestFor(wiremock)).join().collect(Collectors.toList());

        assertEquals("{\"a\":1}", events.get(0).data());
        assertEquals("{\"a\":2}", events.get(1).data());
        assertEquals(SseEvent.DONE_SENTINEL, events.get(2).data());
        assertTrue(events.get(2).isDoneSentinel());
        assertNull(events.get(0).event());
    }

    @Test
    void testStreamHttpError(final WireMockRuntimeInfo wiremock) {
        stubFor(post("/chat/completions").willReturn(aResponse().withStatus(500).withBody("boom")));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var future = transport.stream(requestFor(wiremock));

        final var error = assertThrows(java.util.concurrent.CompletionException.class, () -> future.join());
        final var streamError = assertInstanceOf(OkHttpModelTransport.HttpStreamException.class,
                                                 error.getCause());
        assertEquals(500, streamError.status());
        assertEquals("boom", new String(streamError.body()));
    }

    @Test
    void testStreamMultiLineDataAndComments(final WireMockRuntimeInfo wiremock) {
        final var body = ": keep alive\n\nline one\nline two\n\n";
        stubFor(post("/chat/completions").willReturn(okForContentType("text/event-stream", body)));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var events = transport.stream(requestFor(wiremock)).join().collect(Collectors.toList());

        assertEquals(1, events.size());
        assertEquals("line one\nline two", events.get(0).data());
        assertNull(events.get(0).event());
    }

    @Test
    void testStreamNamedEvents(final WireMockRuntimeInfo wiremock) {
        final var body = "event: message_start\n{\"x\":1}\n\nevent: message_stop\ndata: {\"x\":2}\n\n";
        stubFor(post("/chat/completions").willReturn(okForContentType("text/event-stream", body)));

        final var transport = OkHttpModelTransport.of(new okhttp3.OkHttpClient.Builder().build());
        final var events = transport.stream(requestFor(wiremock)).join().collect(Collectors.toList());

        assertEquals(2, events.size());
        assertEquals("message_start", events.get(0).event());
        assertEquals("message_stop", events.get(1).event());
        assertEquals("{\"x\":1}", events.get(0).data());
        assertEquals("{\"x\":2}", events.get(1).data());
    }
}
