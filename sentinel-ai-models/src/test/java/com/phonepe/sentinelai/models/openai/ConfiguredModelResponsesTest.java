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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.MediaInput;
import com.phonepe.sentinelai.core.agent.RetrySetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.TestAgents;
import com.phonepe.sentinelai.models.TestAgents.OutputObject;
import com.phonepe.sentinelai.models.TestAgents.OutputObjectAgent;
import com.phonepe.sentinelai.models.TestAgents.TestAgent;
import com.phonepe.sentinelai.models.TestStubs;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
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
 * WireMock parity tests for {@link ConfiguredModel} over the OpenAI Responses wire format. The
 * fixtures speak the Responses format: flat {@code function_call} items, top-level
 * {@code instructions} and named SSE events.
 */
@Slf4j
@WireMockTest
class ConfiguredModelResponsesTest {

    /**
     * The Responses endpoint used by the stubs.
     */
    private static final String ENDPOINT = "/responses";

    /**
     * Error body a provider returns when the {@code previous_response_id} of a chained request is
     * unknown or expired (chain break).
     */
    private static final String CHAIN_BREAK_ERROR_BODY = """
            {"error": {"message": "No response found with id 'resp_run1'.", \
            "type": "invalid_request_error", "param": "previous_response_id", \
            "code": "previous_response_id_not_found"}}""";

    /**
     * Plain single-message SSE stream the retried request returns.
     */
    private static final String STREAM_RETRY_SSE_BODY = """
            event: response.output_text.delta
            data: {"delta": "{\\\"output\\\": \\\"Hello, Santanu!\\\"}"}

            event: response.completed
            data: {"response": {"id": "resp_stream", "object": "response", "created_at": 1755064668, \
            "model": "gpt-4o", "status": "completed", "output": [{"type": "message", "role": "assistant", \
            "status": "completed", "content": [{"type": "output_text", "text": "{\\\"output\\\": \\\"Hello, Santanu!\\\"}"}]}], \
            "usage": {"input_tokens": 10, "output_tokens": 5, "total_tokens": 15}}}
            """;

    static Stream<Arguments> generateChainingToolLoopScenarios() {
        // Fresh run without prior history: within the run's tool loop, IN_RUN and SESSION
        // behave identically (both chain the second request to the first response)
        return Stream.of(Arguments.of(ModelOptions.ResponseChaining.OFF, false),
                         Arguments.of(ModelOptions.ResponseChaining.IN_RUN, true),
                         Arguments.of(ModelOptions.ResponseChaining.SESSION, true));
    }

    static Stream<Arguments> generateSeededHistoryChainingScenarios() {
        return Stream.of(Arguments.of("SESSION chains across runs",
                                      ModelOptions.ResponseChaining.SESSION,
                                      historyWithAnchor(),
                                      "resp_run1"),
                         Arguments.of("IN_RUN ignores previous-run anchor",
                                      ModelOptions.ResponseChaining.IN_RUN,
                                      historyWithAnchor(),
                                      null),
                         Arguments.of("compaction breaks the SESSION chain",
                                      ModelOptions.ResponseChaining.SESSION,
                                      historyWithCompaction(),
                                      null));
    }

    static Stream<Arguments> generateStreamingToolLoopScenarios() {
        return Stream.of(Arguments.of("duplicate finish chunk", "resp-duplicate-finish", "Hello, Santanu!", 1, 1),
                         Arguments.of("multiple tool calls with split arguments",
                                      "resp-gpt6sol-multi",
                                      "Hello Santanu",
                                      2,
                                      2),
                         Arguments.of("reasoning then tool call at non-zero index",
                                      "resp-gpt6sol-reasoning",
                                      "Hello Santanu",
                                      1,
                                      1));
    }

    private static ConfiguredModel chainedResponsesModel(final WireMockRuntimeInfo wiremock,
                                                         final ModelOptions.ResponseChaining chaining) {
        return ConfiguredModel.builder()
                .modelName(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"))
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ResponsesProtocol())
                        .endpointPrefix(TestStubs.useRealEndpoints() ? null : Provider.NO_ENDPOINT_PREFIX)
                        .build())
                .modelOptions(ModelOptions.builder()
                        .responseChaining(chaining)
                        .build())
                .build();
    }

    private static AgentSetup.AgentSetupBuilder chainingSetupBase(final WireMockRuntimeInfo wiremock,
                                                                  final ModelOptions.ResponseChaining chaining) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(chainedResponsesModel(wiremock, chaining))
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .build())
                .retrySetup(RetrySetup.builder()
                        .totalAttempts(1)
                        .delayAfterFailedAttempt(Duration.ofMillis(10))
                        .build());
    }

    private static List<AgentMessage> historyWithAnchor() {
        // Run 1 of the session: a user prompt and a stored tool call carrying the
        // provider response id of the response that produced it
        return List.of(UserPrompt.builder()
                .sessionId("s1")
                .runId("run-1")
                .contentType(MediaTypes.MessageContentType.TEXT)
                .content("Hi")
                .build(),
                       new ToolCall("s1",
                                    "run-1",
                                    null,
                                    null,
                                    "call_hist",
                                    "output_object_agent_get_name",
                                    "{}",
                                    "resp_run1"));
    }

    private static List<AgentMessage> historyWithCompaction() {
        // A stored anchor tool call, then a compaction summary prompt (chain breaker), then a
        // follow-up user prompt
        return List.of(new ToolCall("s1",
                                    "run-1",
                                    null,
                                    null,
                                    "call_hist",
                                    "output_object_agent_get_name",
                                    "{}",
                                    "resp_run1"),
                       UserPrompt.builder()
                               .sessionId("s1")
                               .runId("run-1")
                               .contentType(MediaTypes.MessageContentType.TEXT)
                               .content("Summary of earlier conversation")
                               .compacted(true)
                               .build(),
                       UserPrompt.builder()
                               .sessionId("s1")
                               .runId("run-2")
                               .contentType(MediaTypes.MessageContentType.TEXT)
                               .content("Hi again")
                               .build());
    }

    private static ConfiguredModel responsesModel(final WireMockRuntimeInfo wiremock,
                                                  final OkHttpClient httpClient,
                                                  final String apiKey) {
        final var resolvedApiKey = apiKey == null
                ? TestStubs.getTestProperty("AZURE_API_KEY", null)
                : apiKey;
        return ConfiguredModel.builder()
                .modelName(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"))
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ResponsesProtocol())
                        .endpointPrefix(TestStubs.useRealEndpoints() ? null : Provider.NO_ENDPOINT_PREFIX)
                        .auth(resolvedApiKey == null ? null
                                : HeaderAuth.bearer(resolvedApiKey))
                        .build())
                .httpClient(httpClient)
                .build();
    }

    private static AgentSetup.AgentSetupBuilder setupBase(final WireMockRuntimeInfo wiremock) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(responsesModel(wiremock, null, null))
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .build())
                .retrySetup(RetrySetup.builder()
                        .totalAttempts(1)
                        .delayAfterFailedAttempt(Duration.ofMillis(10))
                        .build());
    }

    private static void setupBlockingMocks(final int numStates, final String prefix) {
        IntStream.rangeClosed(1, numStates).forEach(i -> stubFor(post(ENDPOINT).inScenario("model-test")
                .whenScenarioStateIs(i == 1 ? STARTED : Objects.toString(i))
                .willReturn(okForContentType("application/json",
                                             TestStubs.readStubFile(i, prefix, ConfiguredModelResponsesTest.class)))
                .willSetStateTo(Objects.toString(i + 1))));
    }

    @Test
    @SneakyThrows
    void apiKeySetsBearerHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(1, "resp-notools");
        final var mapper = JsonUtils.createMapper();
        final var agent = new OutputObjectAgent(AgentSetup.builder()
                .mapper(mapper)
                .model(responsesModel(wiremock, null, "test-key"))
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        WireMock.verify(WireMock
                .postRequestedFor(WireMock
                        .urlEqualTo(ENDPOINT))
                .withHeader("Authorization",
                            WireMock
                                    .equalTo("Bearer test-key")));
    }

    @Test
    @SneakyThrows
    void blockingRequestLiftsInstructionsAndSendsFlatTools(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(3, "resp-structured-output");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());

        WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT))
                .withRequestBody(matchingJsonPath(
                                                  "$.instructions",
                                                  containing("Greet the user by name and respond to queries")))
                .withRequestBody(matchingJsonPath("$[?(@.store == false)]"))
                .withRequestBody(matchingJsonPath("$.tools[?(@.type == 'function' && @.name == 'output_object_agent_get_name')]")));
    }

    @Test
    @SneakyThrows
    void blockingRequestOmitsStreamFlagAndUsesJsonAcceptHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(1, "resp-notools");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .disableTools(true)
                        .build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(WireMock
                        .notContaining("\"stream\"")));
    }

    @Test
    @SneakyThrows
    void chainBreakRetriesOnStreamingPath(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(ENDPOINT).inScenario("chain-break-stream")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CHAIN_BREAK_ERROR_BODY))
                .willSetStateTo("retried"));
        stubFor(post(ENDPOINT).inScenario("chain-break-stream")
                .whenScenarioStateIs("retried")
                .willReturn(okForContentType("text/event-stream", STREAM_RETRY_SSE_BODY)));

        final var agent = new TestAgent(chainingSetupBase(wiremock,
                                                          ModelOptions.ResponseChaining.SESSION)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .oldMessages(new ArrayList<>(historyWithAnchor()))
                .build(), streamConsumer())
                .join();

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals("Hello, Santanu!", response.getData());
        WireMock.verify(1,
                        postRequestedFor(urlEqualTo(ENDPOINT))
                                .withRequestBody(matchingJsonPath("$.previous_response_id", equalTo("resp_run1"))));
        WireMock.verify(1,
                        postRequestedFor(urlEqualTo(ENDPOINT))
                                .withRequestBody(WireMock.notContaining("previous_response_id"))
                                .withRequestBody(matchingJsonPath("$[?(@.store == true)]")));
    }

    @Test
    @SneakyThrows
    void chainBreakRetriesOnceWithFullHistory(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(ENDPOINT).inScenario("chain-break-retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CHAIN_BREAK_ERROR_BODY))
                .willSetStateTo("retried"));
        stubFor(post(ENDPOINT).inScenario("chain-break-retry")
                .whenScenarioStateIs("retried")
                .willReturn(okForContentType("application/json",
                                             TestStubs.readStubFile(1,
                                                                    "resp-notools",
                                                                    ConfiguredModelResponsesTest.class))));

        final var agent = new OutputObjectAgent(chainingSetupBase(wiremock,
                                                                  ModelOptions.ResponseChaining.SESSION)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
        final var response = agent.execute(AgentInput.<OutputObject>builder()
                .request(new TestAgents.OutputObject(null, "Hi"))
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .oldMessages(new ArrayList<>(historyWithAnchor()))
                .build());

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        // First request chained to the seeded anchor and was rejected
        WireMock.verify(1,
                        postRequestedFor(urlEqualTo(ENDPOINT))
                                .withRequestBody(matchingJsonPath("$.previous_response_id", equalTo("resp_run1"))));
        // Retry carries the full history with no anchor and is stored (it becomes the new anchor)
        WireMock.verify(1,
                        postRequestedFor(urlEqualTo(ENDPOINT))
                                .withRequestBody(WireMock.notContaining("previous_response_id"))
                                .withRequestBody(matchingJsonPath("$.input.length()", equalTo("4")))
                                .withRequestBody(matchingJsonPath(
                                                                  "$.input[?(@.type == 'function_call' && @.call_id == 'call_hist')]"))
                                .withRequestBody(matchingJsonPath("$[?(@.store == true)]")));
    }

    @Test
    @SneakyThrows
    void chainBreakRetryHappensOnlyOnce(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(ENDPOINT).inScenario("chain-break-once")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CHAIN_BREAK_ERROR_BODY))
                .willSetStateTo("second"));
        stubFor(post(ENDPOINT).inScenario("chain-break-once")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CHAIN_BREAK_ERROR_BODY))
                .willSetStateTo("third"));
        stubFor(post(ENDPOINT).inScenario("chain-break-once")
                .whenScenarioStateIs("third")
                .willReturn(okForContentType("application/json",
                                             TestStubs.readStubFile(1,
                                                                    "resp-notools",
                                                                    ConfiguredModelResponsesTest.class))));

        final var agent = new OutputObjectAgent(chainingSetupBase(wiremock,
                                                                  ModelOptions.ResponseChaining.SESSION)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
        final var response = agent.execute(AgentInput.<OutputObject>builder()
                .request(new TestAgents.OutputObject(null, "Hi"))
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .oldMessages(new ArrayList<>(historyWithAnchor()))
                .build());

        // The second failure surfaces; the retry never runs a third time
        assertEquals(ErrorType.MODEL_CALL_HTTP_FAILURE, response.getError().getErrorType());
        WireMock.verify(2, postRequestedFor(urlEqualTo(ENDPOINT)));
    }

    @ParameterizedTest(name = "{0}")
    @SneakyThrows
    @MethodSource("generateChainingToolLoopScenarios")
    void chainingToolLoopBehavior(final ModelOptions.ResponseChaining chaining,
                                  final boolean chained,
                                  final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(4, "resp-tool-output");
        final var agent = new OutputObjectAgent(chainingSetupBase(wiremock, chaining)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(output -> output)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType(), chaining.name());

        if (chained) {
            // First request: full history, stored (the first hop of a chain must be stored)
            WireMock.verify(1,
                            postRequestedFor(urlEqualTo(ENDPOINT))
                                    .withRequestBody(matchingJsonPath("$[?(@.store == true)]"))
                                    .withRequestBody(WireMock.notContaining("previous_response_id")));
            // Second request (after the tool round): chains to the first response id and
            // carries only the delta item (the function_call_output), not the history
            WireMock.verify(1,
                            postRequestedFor(urlEqualTo(ENDPOINT))
                                    .withRequestBody(matchingJsonPath("$.previous_response_id", equalTo("resp_test")))
                                    .withRequestBody(matchingJsonPath("$[?(@.store == true)]"))
                                    .withRequestBody(matchingJsonPath("$.input.length()", equalTo("1")))
                                    .withRequestBody(matchingJsonPath(
                                                                      "$.input[?(@.type == 'function_call_output')].call_id",
                                                                      equalTo("call_PqPR7R6ueMHA71WNOnsMHDOQ"))));
        }
        else {
            // Every request: full history, nothing stored, no chaining markers on the wire
            WireMock.verify(2,
                            postRequestedFor(urlEqualTo(ENDPOINT))
                                    .withRequestBody(matchingJsonPath("$[?(@.store == false)]"))
                                    .withRequestBody(WireMock.notContaining("previous_response_id"))
                                    .withRequestBody(WireMock.notContaining("_responseId"))
                                    .withRequestBody(WireMock.notContaining("_runId")));
            // The tool call item is replayed as history on the second request
            WireMock.verify(1,
                            postRequestedFor(urlEqualTo(ENDPOINT))
                                    .withRequestBody(matchingJsonPath("$.input[?(@.type == 'function_call')]"))
                                    .withRequestBody(matchingJsonPath("$[?(@.store == false)]")));
        }
    }

    @Test
    @SneakyThrows
    void nonChainBreakErrorIsNotRetried(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(ENDPOINT)
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": {"message": "The model `gpt-4o` does not exist", \
                                "type": "invalid_request_error", "param": "model"}}""")));

        final var agent = new OutputObjectAgent(chainingSetupBase(wiremock,
                                                                  ModelOptions.ResponseChaining.SESSION)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
        final var response = agent.execute(AgentInput.<OutputObject>builder()
                .request(new TestAgents.OutputObject(null, "Hi"))
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .oldMessages(new ArrayList<>(historyWithAnchor()))
                .build());

        assertEquals(ErrorType.MODEL_CALL_HTTP_FAILURE, response.getError().getErrorType());
        WireMock.verify(1, postRequestedFor(urlEqualTo(ENDPOINT)));
    }

    @ParameterizedTest(name = "{0}")
    @SneakyThrows
    @MethodSource("generateSeededHistoryChainingScenarios")
    void seededHistoryChainingBehavior(final String name,
                                       final ModelOptions.ResponseChaining chaining,
                                       final List<AgentMessage> history,
                                       final String expectedPreviousResponseId,
                                       final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(4, "resp-tool-output");
        final var agent = new OutputObjectAgent(chainingSetupBase(wiremock, chaining)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(output -> output)
                .build());

        agent.execute(AgentInput.<OutputObject>builder()
                .request(new TestAgents.OutputObject(null, "Hi"))
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .oldMessages(new ArrayList<>(history))
                .build());

        final var firstRequest = postRequestedFor(urlEqualTo(ENDPOINT))
                .withRequestBody(matchingJsonPath("$[?(@.store == true)]"))
                .withRequestBody(WireMock.notContaining("_responseId"))
                .withRequestBody(WireMock.notContaining("_compacted"));
        if (expectedPreviousResponseId != null) {
            WireMock.verify(1,
                            firstRequest
                                    .withRequestBody(matchingJsonPath("$.previous_response_id",
                                                                      equalTo(expectedPreviousResponseId)))
                                    .withRequestBody(matchingJsonPath("$.input.length()", equalTo("2")))
                                    .withRequestBody(WireMock.notContaining("call_hist")));
        }
        else {
            WireMock.verify(1,
                            firstRequest.withRequestBody(WireMock.notContaining("previous_response_id")));
        }
    }

    @Test
    @SneakyThrows
    void streamingImageUpload(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(ENDPOINT).willReturn(okForContentType("text/event-stream",
                                                           TestStubs.readStubFile(1,
                                                                                  "resp-image-stream",
                                                                                  ConfiguredModelResponsesTest.class))));
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
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }

    @Test
    @SneakyThrows
    void streamingRequestCarriesStreamFlagAndSseAcceptHeader(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupSseMocks(5, "resp-events");
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), streamConsumer())
                .join();

        WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT))
                .withHeader("Accept", equalTo("text/event-stream"))
                .withRequestBody(matchingJsonPath("$[?(@.stream == true)]")));
    }

    @Test
    @SneakyThrows
    void streamingToolLoop(final WireMockRuntimeInfo wiremock) {
        setupSseMocks(5, "resp-events");
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

    @ParameterizedTest(name = "{0}")
    @SneakyThrows
    @MethodSource("generateStreamingToolLoopScenarios")
    void streamingToolLoopDecoding(final String name,
                                   final String fixturePrefix,
                                   final String expectedData,
                                   final int expectedToolCalls,
                                   final int expectedToolResponses,
                                   final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupSseMocks(2, fixturePrefix);
        final var agent = new TestAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());
        final var response = agent.executeAsyncStreaming(AgentInput.<String>builder()
                .request("Hi")
                .build(), streamConsumer())
                .join();
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType(), name);
        assertEquals(expectedData, response.getData(), name);
        assertEquals(1, agent.getNameCalls.get(), name);
        assertEquals(expectedToolCalls, countMessages(response.getAllMessages(), ToolCall.class), name);
        assertEquals(expectedToolResponses, countMessages(response.getAllMessages(), ToolCallResponse.class), name);
        assertTrue(response.getUsage().getTotalTokens() > 1, name);
    }

    @Test
    @SneakyThrows
    void structuredOutputSendsTextFormatSchema(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(1, "resp-notools");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT))
                .withRequestBody(matchingJsonPath(
                                                  "$.text.format[?(@.type == 'json_schema')].name",
                                                  equalTo("model_output"))));
    }

    @Test
    @SneakyThrows
    void structuredOutputToolLoop(final WireMockRuntimeInfo wiremock) {
        setupBlockingMocks(3, "resp-structured-output");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertEquals("Santanu", response.getData().username());
        assertTrue(response.getUsage().getTotalTokens() > 1);
        assertTrue(countMessages(response.getAllMessages(), ToolCall.class) >= 1);
        assertTrue(countMessages(response.getAllMessages(), ToolCallResponse.class) >= 1);
    }

    @Test
    @SneakyThrows
    void toolBasedOutputRun(final WireMockRuntimeInfo wiremock) {
        setupBlockingMocks(4, "resp-tool-output");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .outputGenerationTool(output -> output)
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertTrue(countMessages(response.getAllMessages(), ToolCall.class) >= 1);
        assertTrue(countMessages(response.getAllMessages(), ToolCallResponse.class) >= 1);
    }

    @Test
    @SneakyThrows
    void toolsDisabledRun(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupBlockingMocks(1, "resp-notools");
        final var agent = new OutputObjectAgent(setupBase(wiremock)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .disableTools(true)
                        .build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertEquals(1, response.getUsage().getRequestsForRun());
        assertTrue(response.getUsage().getTotalTokens() > 1);

        WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT))
                .withRequestBody(WireMock
                        .notContaining("\"tools\"")));
    }

    @Test
    @SneakyThrows
    void userOkHttpClientPathWorks(final WireMockRuntimeInfo wiremock) {
        setupBlockingMocks(1, "resp-notools");
        final var mapper = JsonUtils.createMapper();
        final var httpClient = new OkHttpClient.Builder().build();
        final var agent = new OutputObjectAgent(AgentSetup.builder()
                .mapper(mapper)
                .model(responsesModel(wiremock, httpClient, null))
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());

        final var response = execute(agent);
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    private void setupSseMocks(final int numStates, final String prefix) {
        IntStream.rangeClosed(1, numStates).forEach(i -> stubFor(post(ENDPOINT)
                .inScenario("model-test")
                .whenScenarioStateIs(i == 1 ? STARTED : Objects.toString(i))
                .willReturn(okForContentType("text/event-stream",
                                             TestStubs.readStubFile(i, prefix, ConfiguredModelResponsesTest.class)))
                .willSetStateTo(Objects.toString(i + 1))));
    }
}
