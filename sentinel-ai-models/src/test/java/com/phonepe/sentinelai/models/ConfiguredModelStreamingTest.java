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

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.MediaInput;
import com.phonepe.sentinelai.core.agent.StreamConsumer;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.TestAgents.TestAgent;
import com.phonepe.sentinelai.models.TestStubs;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.phonepe.sentinelai.models.TestAgents.countMessages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests streaming with {@link ConfiguredModel}. Port of the old
 * {@code SimpleOpenAIModelStreamingTest}; SSE fixtures are identical, the endpoint is plain
 * {@code /chat/completions}.
 */
@Slf4j
@WireMockTest
class ConfiguredModelStreamingTest {

    private static StreamConsumer createStreamConsumer(final PrintStream outputStream) {
        return new StreamConsumer() {
            @Override
            public void consumeContent(final String content) {
                print(content.getBytes(), outputStream);
            }
        };
    }

    private static AgentOutput<String> execute(final WireMockRuntimeInfo wiremock,
                                               final OkHttpClient client) throws FileNotFoundException {
        final var objectMapper = JsonUtils.createMapper();

        final var stats = new ModelUsageStats(); // We want to collect stats from the whole session
        final var executor = Executors.newCachedThreadPool();
        final var agent = setupAgent(wiremock, objectMapper, client, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        return agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .usageStats(stats)
                        .build())
                .build(), createStreamConsumer(outputStream))
                .join();
    }

    private static void print(final byte[] data, final PrintStream outputStream) {
        try {
            outputStream.write(data);
            outputStream.flush();
            log.info("RECEIVED: {}", new String(data));
        }
        catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TestAgent setupAgent(final WireMockRuntimeInfo wiremock,
                                        final com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                        final OkHttpClient httpClient,
                                        final ExecutorService executor) {
        final var apiKey = TestStubs.useRealEndpoints()
                ? TestStubs.getTestProperty("AZURE_API_KEY", null)
                : null;
        final var model = ConfiguredModel.builder()
                .modelName(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"))
                .provider(Provider.builder()
                        .baseUrl(TestStubs.getTestProperty("AZURE_ENDPOINT", wiremock.getHttpBaseUrl()))
                        .protocol(new ChatCompletionsProtocol())
                        .auth(apiKey == null ? null : HeaderAuth.bearer(apiKey))
                        .build())
                .httpClient(httpClient)
                .modelOptions(ModelOptions.builder()
                        .toolChoice(ModelOptions.ToolChoice.AUTO)
                        .build())
                .build();
        return new TestAgent(AgentSetup.builder()
                .model(model)
                .mapper(objectMapper)
                .modelSettings(ModelSettings.builder()
                        .parallelToolCalls(false)
                        .temperature(0.1f)
                        .seed(1)
                        .build())
                .executorService(executor)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
    }

    @Test
    @SneakyThrows
    void duplicateFinishChunk(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(2, "duplicate-finish", getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), createStreamConsumer(outputStream))
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1, agent.getNameCalls.get());
        assertEquals(1, countMessages(response.getAllMessages(), ToolCall.class));
        assertEquals(1, countMessages(response.getAllMessages(), ToolCallResponse.class));
    }

    @Test
    @SneakyThrows
    void duplicateStopChunk(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "duplicate-stop", getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), createStreamConsumer(outputStream))
                .join();
        assertEquals(1, countMessages(response.getAllMessages(), StructuredOutput.class));
    }

    @Test
    @SneakyThrows
    void errorBodyReadFailureCompletesStream(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.ENDPOINT)
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(503)
                        .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        final var httpClient = new OkHttpClient.Builder().readTimeout(Duration.ofMillis(300)).build();
        final var response = execute(wiremock, httpClient);
        assertNotNull(response.getError());
    }


    @Test
    @SneakyThrows
    void streamingClosesResponseBody(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "duplicate-stop", getClass());
        final var closed = new AtomicBoolean();
        final var httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(chain -> {
                    final var response = chain.proceed(chain.request());
                    final var original = response.body();
                    final var source = new ForwardingSource(original.source()) {
                        @Override
                        public void close() throws java.io.IOException {
                            closed.set(true);
                            super.close();
                        }
                    };
                    final ResponseBody body = new ResponseBody() {
                        @Override
                        public long contentLength() {
                            return original.contentLength();
                        }

                        @Override
                        public MediaType contentType() {
                            return original.contentType();
                        }

                        @Override
                        public BufferedSource source() {
                            return Okio.buffer(source);
                        }
                    };
                    return response.newBuilder().body(body).build();
                })
                .build();

        final var response = execute(wiremock, httpClient);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(closed.get(), "The stream response body must close after the model call");
    }

    @Test
    @SneakyThrows
    void testAgent(final WireMockRuntimeInfo wiremock) {
        // Setup stub for SSE
        setupSseStubs();
        final var objectMapper = JsonUtils.createMapper();

        final var stats = new ModelUsageStats(); // We want to collect stats from the whole session
        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .usageStats(stats)
                        .build())
                .build(), createStreamConsumer(outputStream))
                .join();
        var responseString = response.getData();
        log.info("Agent response: {}", responseString);
        assertNotNull(responseString);
        // The following needs to be done because the model is not deterministic and might call
        // tools at different times across runs
        final var sunnyFound = new AtomicBoolean(responseString.contains("sunny"));
        final var nameFound = new AtomicBoolean(responseString.contains("Santanu"));
        assertTrue(response.getUsage().getTotalTokens() > 1); // All chunks consumed
        final var response2 = agent.executeAsyncTextStreaming(AgentInput.<String>builder()
                .request("How is the weather?")
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .usageStats(stats)
                        .build())
                .oldMessages(response.getAllMessages())
                .build(), createStreamConsumer(outputStream))
                .join();
        responseString = response2.getData();
        log.info("Agent response: {}", responseString);
        sunnyFound.compareAndSet(false, responseString.contains("sunny"));
        nameFound.compareAndSet(false, responseString.contains("Santanu"));
        assertTrue(sunnyFound.get() && nameFound.get());
        assertTrue(response2.getUsage().getTotalTokens() > 1);
        assertTrue(stats.getTotalTokens() > 1);
        log.info("Session stats: {}", stats);
    }

    @Test
    @SneakyThrows
    void testImageUploadStreaming(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        // Setup stub for SSE with image response
        stubFor(post(TestStubs.ENDPOINT).willReturn(okForContentType("text/event-stream",
                                                                     TestStubs.readStubFile(1,
                                                                                            "image-stream",
                                                                                            getClass()))));

        final var objectMapper = JsonUtils.createMapper();
        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var base64Image = "data:image/png;base64,iVBORw0KGgoAAAANS";
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Describe the image")
                .media(List.of(MediaInput.imageContent(base64Image, ImageDetail.AUTO)))
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .build(), createStreamConsumer(outputStream))
                .join();

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertTrue(response.getData().contains("A man with dark hair and glasses"));
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }

    @Test
    @SneakyThrows
    void testPreProcessorIsCalled(final WireMockRuntimeInfo wiremock) {
        setupSseStubs();
        final var objectMapper = JsonUtils.createMapper();

        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        final var preProcessorCalled = new AtomicBoolean(false);
        agent.registerAgentMessagesPreProcessor((ctx, allMessages, newMessages) -> {
            preProcessorCalled.set(true);
            return new AgentMessagesPreProcessResult(null, null);
        });

        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), createStreamConsumer(outputStream))
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(preProcessorCalled.get());
    }

    @Test
    @SneakyThrows
    void testPreProcessorsThrowingException(final WireMockRuntimeInfo wiremock) {
        setupSseStubs();
        final var objectMapper = JsonUtils.createMapper();

        final var executor = Executors.newCachedThreadPool();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = setupAgent(wiremock, objectMapper, httpClient, executor);
        agent.registerAgentMessagesPreProcessor((ctx, allMessages, newMessages) -> {
            throw new RuntimeException("Errored");
        });

        final var outputStream = new PrintStream(new FileOutputStream("/dev/stdout"), true);
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), createStreamConsumer(outputStream))
                .join();
        assertEquals(ErrorType.PREPROCESSOR_RUN_FAILURE, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testTimeouts(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocksWithTimeout(Duration.ofSeconds(1));

        final var httpClient = new OkHttpClient.Builder().readTimeout(Duration.ofMillis(100)).build();

        final var response = execute(wiremock, httpClient);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                   response.getError().getErrorType(),
                   "Expected TIMEOUT after retries, got: " + response.getError());
    }

    private void setupSseStubs() {
        // Setup stub for SSE
        IntStream.rangeClosed(1, 5).forEach(i -> stubFor(post(TestStubs.ENDPOINT)
                .inScenario("model-test")
                .whenScenarioStateIs(i == 1 ? Scenario.STARTED : Objects.toString(i))
                .willReturn(okForContentType("text/event-stream",
                                             TestStubs.readStubFile(i, "events", getClass())))
                .willSetStateTo(Objects.toString(i + 1))));
    }
}
