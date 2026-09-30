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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.RetrySetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.Provider;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the {@link RequestRetryPolicy} wiring of {@link ConfiguredModel} over WireMock:
 * retries on the configured statuses, the Retry-After header honoring and the default
 * no-retry behavior.
 */
@Slf4j
@WireMockTest
class RequestRetryTest {

    private static final class TestAgent
            extends
            Agent<OutputObject, OutputObject, TestAgent> {

        private TestAgent(final AgentSetup setup) {
            super(OutputObject.class,
                  "Greet the user",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    private static AgentSetup agentSetup(final ConfiguredModel model) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(model)
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .retrySetup(RetrySetup.builder()
                        .totalAttempts(1)
                        .delayAfterFailedAttempt(Duration.ofMillis(10))
                        .build())
                .build();
    }

    private static AgentOutput<OutputObject> execute(final TestAgent agent) {
        return agent.execute(AgentInput.<OutputObject>builder().request(new OutputObject(null, "Hi")).build());
    }

    private static ConfiguredModel modelWithRetry(final WireMockRuntimeInfo wiremock,
                                                  final RequestRetryPolicy retryPolicy) {
        return ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .requestRetryPolicy(retryPolicy)
                .build();
    }

    @Test
    @SneakyThrows
    void defaultPolicyDoesNotRetry(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).willReturn(aResponse().withStatus(500).withBody("failure")));
        final var model = modelWithRetry(wiremock, null);
        final var agent = new TestAgent(agentSetup(model));

        final var response = execute(agent);

        assertEquals(ErrorType.MODEL_CALL_HTTP_FAILURE, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(1, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    @SneakyThrows
    void nonRetryableStatusIsNotRetried(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).willReturn(aResponse().withStatus(400).withBody("bad request")));
        final var model = modelWithRetry(wiremock,
                                         RequestRetryPolicy.builder()
                                                 .maxAttempts(3)
                                                 .initialDelay(Duration.ofMillis(10))
                                                 .build());
        final var agent = new TestAgent(agentSetup(model));

        final var response = execute(agent);

        assertEquals(ErrorType.MODEL_CALL_HTTP_FAILURE, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(1, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    @SneakyThrows
    void retriesFailuresThenSucceeds(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).inScenario("retry-test")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody("first failure"))
                .willSetStateTo("2"));
        stubFor(post(TestStubs.ENDPOINT).inScenario("retry-test")
                .whenScenarioStateIs("2")
                .willReturn(aResponse().withStatus(503).withBody("second failure"))
                .willSetStateTo("3"));
        stubFor(post(TestStubs.ENDPOINT).inScenario("retry-test")
                .whenScenarioStateIs("3")
                .willReturn(okForContentType("application/json",
                                             TestStubs.readStubFile(1, "no-tools", RequestRetryTest.class))));
        final var model = modelWithRetry(wiremock,
                                         RequestRetryPolicy.builder()
                                                 .maxAttempts(3)
                                                 .initialDelay(Duration.ofMillis(10))
                                                 .build());
        final var agent = new TestAgent(agentSetup(model));

        final var response = execute(agent);

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(3, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    @SneakyThrows
    void retryAfterHeaderIsHonored(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).inScenario("retry-after-test")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1").withBody("slow down"))
                .willSetStateTo("2"));
        stubFor(post(TestStubs.ENDPOINT).inScenario("retry-after-test")
                .whenScenarioStateIs("2")
                .willReturn(okForContentType("application/json",
                                             TestStubs.readStubFile(1, "no-tools", RequestRetryTest.class))));
        final var model = modelWithRetry(wiremock,
                                         RequestRetryPolicy.builder()
                                                 .maxAttempts(2)
                                                 .initialDelay(Duration.ofMillis(10))
                                                 .build());
        final var agent = new TestAgent(agentSetup(model));

        final var startedAt = System.nanoTime();
        final var response = execute(agent);
        final var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(elapsed.compareTo(Duration.ofSeconds(1)) >= 0,
                   "Retry-After: 1 must delay the retry by at least one second, took " + elapsed);
        com.github.tomakehurst.wiremock.client.WireMock.verify(2, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    private record OutputObject(
            String username,
            String message
    ) {
    }

}
