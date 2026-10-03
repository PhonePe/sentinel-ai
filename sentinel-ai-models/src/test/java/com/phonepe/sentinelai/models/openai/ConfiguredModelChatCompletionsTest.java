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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.MediaInput;
import com.phonepe.sentinelai.core.agent.RetrySetup;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.TestAgents.OutputObjectAgent;
import com.phonepe.sentinelai.models.TestAgents.TestAgent;
import com.phonepe.sentinelai.models.TestStubs;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.phonepe.sentinelai.models.TestAgents.countMessages;
import static com.phonepe.sentinelai.models.TestAgents.execute;
import static com.phonepe.sentinelai.models.TestAgents.streamConsumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WireMock parity tests for {@link ConfiguredModel} with the Chat Completions protocol: the tool
 * loop, structured output and streaming over the real Chat Completions wire format. Fixtures and
 * scenario flow are identical to the ConfiguredModel tests; this class proves the parity against
 * the production protocol.
 */
@Slf4j
@WireMockTest
class ConfiguredModelChatCompletionsTest {

    private static ConfiguredModel chatModel(final WireMockRuntimeInfo wiremock,
                                             final OkHttpClient httpClient,
                                             final String apiKey) {
        final var resolvedApiKey = apiKey == null
                ? TestStubs.getTestProperty("AZURE_API_KEY", null)
                : apiKey;
        return ConfiguredModel.builder()
                .modelName(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"))
                .provider(Provider.builder()
                        .baseUrl(TestStubs.getTestProperty("AZURE_ENDPOINT", wiremock.getHttpBaseUrl()))
                        .protocol(new ChatCompletionsProtocol())
                        .auth(resolvedApiKey == null ? null
                                : HeaderAuth.bearer(resolvedApiKey))
                        .build())
                .httpClient(httpClient)
                .build();
    }

    private static AgentSetup.AgentSetupBuilder setupBase(final WireMockRuntimeInfo wiremock) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(chatModel(wiremock, null, null))
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .seed(42)
                        .build())
                .retrySetup(RetrySetup.builder()
                        .totalAttempts(1)
                        .delayAfterFailedAttempt(Duration.ofMillis(10))
                        .build());
    }

    @Test
    @SneakyThrows
    void apiKeySetsBearerHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", ConfiguredModelChatCompletionsTest.class);
        final var mapper = JsonUtils.createMapper();
        stubFor(post(TestStubs.ENDPOINT).willReturn(okForContentType("application/json",
                                                                     TestStubs.readStubFile(1,
                                                                                            "no-tools",
                                                                                            ConfiguredModelChatCompletionsTest.class))));
        final var agent = new OutputObjectAgent(AgentSetup.builder()
                .mapper(mapper)
                .model(chatModel(wiremock, null, "test-key"))
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        WireMock.verify(WireMock
                .postRequestedFor(WireMock
                        .urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("Authorization",
                            WireMock
                                    .equalTo("Bearer test-key")));

    }

    @Test
    @SneakyThrows
    void blockingRequestOmitsStreamFlagAndUsesJsonAcceptHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", ConfiguredModelChatCompletionsTest.class);
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .seed(42)
                        .disableTools(true)
                        .build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(notContaining("\"stream\"")));
    }

    @Test
    @SneakyThrows
    void streamingDuplicateFinishChunk(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(2, "duplicate-finish", ConfiguredModelChatCompletionsTest.class);
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), streamConsumer())
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1, agent.getNameCalls.get());
        assertEquals(1, countMessages(response.getAllMessages(), ToolCall.class));
        assertEquals(1, countMessages(response.getAllMessages(), ToolCallResponse.class));
    }

    @Test
    @SneakyThrows
    void streamingImageUpload(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).willReturn(okForContentType("text/event-stream",
                                                                     TestStubs.readStubFile(1,
                                                                                            "image-stream",
                                                                                            ConfiguredModelChatCompletionsTest.class))));
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Describe the image")
                .media(List.of(MediaInput.imageContent("data:image/png;base64,iVBORw0KGgoAAAANS",
                                                       ImageDetail.AUTO)))
                .build(), streamConsumer())
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(response.getData().contains("A man with dark hair and glasses"));
    }

    @Test
    @SneakyThrows
    void streamingRequestCarriesStreamFlagAndSseAcceptHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupSseStubs();
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), streamConsumer())
                .join();

        WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("Accept", equalTo("text/event-stream"))
                .withRequestBody(matchingJsonPath("$[?(@.stream == true)]")));
    }

    @Test
    @SneakyThrows
    void streamingToolLoop(final WireMockRuntimeInfo wiremock) {
        setupSseStubs();
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder().sessionId("s1").userId("ss").build())
                .build(), streamConsumer())
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }

    @Test
    @SneakyThrows
    void structuredOutputToolLoop(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(3, "structured-output", ConfiguredModelChatCompletionsTest.class);
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }

    @Test
    @SneakyThrows
    void textStreamingOmitsResponseFormatAndRequestsUsage(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT).willReturn(okForContentType("text/event-stream",
                                                                     TestStubs.readStubFile(1,
                                                                                            "duplicate-stop",
                                                                                            ConfiguredModelChatCompletionsTest.class))));
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = agent.executeAsyncTextStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder().sessionId("s1").userId("ss").build())
                .build(), streamConsumer())
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withRequestBody(notContaining("response_format"))
                .withRequestBody(matchingJsonPath("$.stream_options[?(@.include_usage == true)]")));
    }

    @Test
    @SneakyThrows
    void toolBasedOutputRun(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(4, "tool-output", ConfiguredModelChatCompletionsTest.class);
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(output -> output)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(countMessages(response.getAllMessages(), ToolCall.class) >= 1);
        assertTrue(countMessages(response.getAllMessages(), ToolCallResponse.class) >= 1);
    }

    @Test
    @SneakyThrows
    void toolsDisabledRun(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(1, "no-tools", ConfiguredModelChatCompletionsTest.class);
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .seed(42)
                        .disableTools(true)
                        .build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertEquals(1, response.getUsage().getRequestsForRun());
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }

    @Test
    @SneakyThrows
    void userOkHttpClientPathWorks(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(1, "no-tools", ConfiguredModelChatCompletionsTest.class);
        final var mapper = JsonUtils.createMapper();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = new OutputObjectAgent(AgentSetup.builder()
                .mapper(mapper)
                .model(chatModel(wiremock, httpClient, null))
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    private void setupSseStubs() {
        IntStream.rangeClosed(1, 5).forEach(i -> stubFor(post(TestStubs.ENDPOINT)
                .inScenario("model-test")
                .whenScenarioStateIs(i == 1 ? STARTED : Objects.toString(i))
                .willReturn(okForContentType("text/event-stream",
                                             TestStubs.readStubFile(i,
                                                                    "events",
                                                                    ConfiguredModelChatCompletionsTest.class)))
                .willSetStateTo(Objects.toString(i + 1))));
    }
}
