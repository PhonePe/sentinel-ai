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

import com.phonepe.sentinelai.core.utils.EnvLoader;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

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
 * WireMock stub helpers for the neutral {@code /v1/chat/completions} endpoint. Mirrors
 * {@code TestUtils} in sentinel-ai-core, which stubs the old Azure dialect URL. Also
 * carries the real-endpoint switch: run the tests against a live provider with
 * {@code mvn -Preal-tests} and a {@code .env} file at the repository root.
 */
@UtilityClass
@Slf4j
public class TestStubs {

    /**
     * The neutral Chat Completions endpoint used by the test wire protocol.
     */
    /**
     * The neutral Chat Completions endpoint with the default /v1 prefix.
     */
    public static final String ENDPOINT = "/v1/chat/completions";

    /**
     * The neutral Chat Completions endpoint without a prefix, used by test providers
     * that declare {@link Provider#NO_ENDPOINT_PREFIX}.
     */
    public static final String NO_PREFIX_ENDPOINT = "/chat/completions";

    /**
     * Reads a test property. In mock mode returns {@code mockValue}; in real mode (system
     * property {@code sentinelai.useRealEndpoints=true}, set by the {@code real-tests} Maven
     * profile) reads the value from the environment or the {@code .env} file.
     *
     * @param variable  the environment variable to read in real mode.
     * @param mockValue the value to return in mock mode.
     * @return the effective value.
     */
    public static String getTestProperty(final String variable, final String mockValue) {
        if (useRealEndpoints()) {
            final var value = EnvLoader.readEnv(variable, mockValue);
            log.info("Using real endpoint for {}: {}", variable, value);
            return value;
        }
        log.info("Using mock endpoint for {}: {}", variable, mockValue);
        return mockValue;
    }

    @SneakyThrows
    public static String readStubFile(final int i, final String prefix, final Class<?> clazz) {
        return Files.readString(Path.of(Objects.requireNonNull(clazz
                .getResource("/wiremock/%s.%d.json".formatted(prefix, i))).toURI()));
    }

    public static void setupMocks(final int numStates, final String prefix, final Class<?> clazz) {
        IntStream.rangeClosed(1, numStates).forEach(i -> {
            stubFor(post(NO_PREFIX_ENDPOINT).inScenario("model-test")
                    .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                    .willReturn(okForContentType("application/json", readStubFile(i, prefix, clazz)))
                    .willSetStateTo(Objects.toString(i + 1)));
            stubFor(post(ENDPOINT).inScenario("model-test-v1")
                    .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                    .willReturn(okForContentType("application/json", readStubFile(i, prefix, clazz)))
                    .willSetStateTo(Objects.toString(i + 1)));
        });
    }

    public static void setupMocksWithFault(final Fault fault) {
        stubFor(post(NO_PREFIX_ENDPOINT).willReturn(aResponse().withFault(fault)));
        stubFor(post(ENDPOINT).willReturn(aResponse().withFault(fault)));
    }

    public static void setupMocksWithTimeout(final Duration duration) {
        stubFor(post(NO_PREFIX_ENDPOINT).willReturn(aResponse().withStatus(200)
                .withFixedDelay((int) duration.toMillis())));
        stubFor(post(ENDPOINT).willReturn(aResponse().withStatus(200)
                .withFixedDelay((int) duration.toMillis())));
    }

    /**
     * @return true when the tests must run against real endpoints.
     */
    public static boolean useRealEndpoints() {
        return "true".equalsIgnoreCase(System.getProperty("sentinelai.useRealEndpoints"));
    }
}
