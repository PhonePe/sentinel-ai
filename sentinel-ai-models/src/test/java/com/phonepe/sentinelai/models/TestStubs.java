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

package com.phonepe.sentinelai.models;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

/**
 * WireMock stub helpers for the neutral {@code /chat/completions} endpoint. Mirrors
 * {@code TestUtils} in sentinel-ai-core, which stubs the old Azure dialect URL.
 */
@UtilityClass
public class TestStubs {

    /**
     * The neutral Chat Completions endpoint used by the test wire protocol.
     */
    public static final String ENDPOINT = "/chat/completions";

    @SneakyThrows
    public static String readStubFile(final int i, final String prefix, final Class<?> clazz) {
        return Files.readString(Path.of(Objects.requireNonNull(clazz
                .getResource("/wiremock/%s.%d.json".formatted(prefix, i))).toURI()));
    }

    public static void setupMocks(final int numStates, final String prefix, final Class<?> clazz) {
        IntStream.rangeClosed(1, numStates).forEach(i -> stubFor(post(ENDPOINT).inScenario("model-test")
                .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                .willReturn(okForContentType("application/json", readStubFile(i, prefix, clazz)))
                .willSetStateTo(Objects.toString(i + 1))));
    }

    public static void setupMocksWithFault(final Fault fault) {
        stubFor(post(ENDPOINT).willReturn(aResponse().withFault(fault)));
    }

    public static void setupMocksWithTimeout(final Duration duration) {
        stubFor(post(ENDPOINT).willReturn(aResponse().withStatus(200)
                .withFixedDelay((int) duration.toMillis())));
    }
}
