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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.transformer.RequestTransformer;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.wire.WireContext;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the request transformer pipeline of {@link ConfiguredModel} over the production
 * Chat Completions protocol: the extension, provider and model levels, their order, the
 * header application, the auth-before-transformers rule and the fail-loud behavior.
 */
@Slf4j
@WireMockTest
class RequestTransformerTest {

    /**
     * Transformer that appends a marker to the body field {@code markers} through the
     * extension contract.
     */
    private static final class ExtensionTransformer implements RequestTransformer {

        private final String marker;
        private final AtomicInteger calls = new AtomicInteger();

        private ExtensionTransformer(final String marker) {
            this.marker = marker;
        }

        @Override
        public void transform(final String requestId, final ObjectNode body, final Map<String, String> headers) {
            calls.incrementAndGet();
            body.put("markers", body.path("markers").asText("") + marker);
            headers.put("X-Extension-Transformer", marker);
        }
    }

    /**
     * Transformer that appends a marker to the body field {@code markers} and counts the
     * calls.
     */
    private static final class MarkerTransformer
            implements
            com.phonepe.sentinelai.models.provider.RequestTransformer {

        private final String marker;
        private final AtomicInteger calls = new AtomicInteger();

        private MarkerTransformer(final String marker) {
            this.marker = marker;
        }

        @Override
        public void transform(final okhttp3.Request.Builder requestBuilder,
                              final ObjectNode body,
                              final WireContext ctx) {
            calls.incrementAndGet();
            body.put("markers", body.path("markers").asText("") + marker);
            requestBuilder.header("X-Transformer", marker);
        }
    }

    private static final class TestAgent extends Agent<OutputObject, OutputObject, TestAgent> {

        private TestAgent(final AgentSetup setup,
                          final List<AgentExtension<OutputObject, OutputObject, TestAgent>> extensions) {
            super(OutputObject.class,
                  "Greet the user",
                  setup,
                  extensions,
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    /**
     * Agent extension that contributes one extension-level transformer per run.
     */
    private static final class TransformerExtension implements AgentExtension<OutputObject, OutputObject, TestAgent> {

        private final RequestTransformer transformer;

        private TransformerExtension(final RequestTransformer transformer) {
            this.transformer = transformer;
        }

        @Override
        public ExtensionPromptSchema additionalSystemPrompts(final OutputObject request,
                                                             final AgentRunContext<OutputObject> context,
                                                             final TestAgent agent,
                                                             final ProcessingMode processingMode) {
            return new ExtensionPromptSchema(List.of());
        }

        @Override
        public List<FactList> facts(final OutputObject request,
                                    final AgentRunContext<OutputObject> context,
                                    final TestAgent agent) {
            return List.of();
        }

        @Override
        public String name() {
            return "transformer-extension";
        }

        @Override
        public Optional<ModelOutputDefinition> outputSchema(final ProcessingMode processingMode) {
            return Optional.empty();
        }

        @Override
        public List<RequestTransformer> requestTransformers(final OutputObject request,
                                                            final AgentRunContext<OutputObject> context,
                                                            final TestAgent agent) {
            return List.of(transformer);
        }
    }

    private static AgentSetup agentSetup(final Model model) {
        return AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .model(model)
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build();
    }

    private static ConfiguredModel baseModel(final WireMockRuntimeInfo wiremock,
                                             final com.phonepe.sentinelai.models.provider.RequestTransformer providerTransformer,
                                             final com.phonepe.sentinelai.models.provider.RequestTransformer modelTransformer) {
        return ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .requestTransformers(providerTransformer == null
                                ? List.of()
                                : List.of(providerTransformer))
                        .build())
                .requestTransformers(modelTransformer == null ? List.of() : List.of(modelTransformer))
                .build();
    }

    private static AgentOutput<OutputObject> execute(final TestAgent agent) {
        return agent.execute(AgentInput.<OutputObject>builder().request(new OutputObject(null, "Hi")).build());
    }

    private static void setupMocks() {
        TestStubs.setupMocks(1, "no-tools", RequestTransformerTest.class);
    }

    @Test
    @SneakyThrows
    void authIsAppliedBeforeTransformers(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupMocks();
        final var seenAuth = new AtomicInteger(0);
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .requestTransformers(List.of((requestBuilder, body, ctx) -> {
                    final var authorization = requestBuilder.build().header("Authorization");
                    if ("Bearer test-key".equals(authorization)) {
                        seenAuth.incrementAndGet();
                    }
                }))
                .build();
        final var agent = new TestAgent(agentSetup(model),
                                        List.of(new TransformerExtension(new ExtensionTransformer("x"))));

        final var response = execute(agent);

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1, seenAuth.get(), "Auth must be applied before the transformers");
        com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("Authorization", equalTo("Bearer test-key")));
    }

    @Test
    @SneakyThrows
    void failingExtensionTransformerFailsLoud(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupMocks();
        final var failing = new RequestTransformer() {
            @Override
            public void transform(final String requestId, final ObjectNode body, final Map<String, String> headers) {
                throw new IllegalStateException("Extension transformer failed");
            }
        };
        final var model = baseModel(wiremock, new MarkerTransformer("provider"), new MarkerTransformer("model"));
        final var agent = new TestAgent(agentSetup(model), List.of(new TransformerExtension(failing)));

        final var response = execute(agent);

        assertEquals(ErrorType.REQUEST_TRANSFORM_FAILED, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(0, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    @SneakyThrows
    void failingModelTransformerFailsLoud(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupMocks();
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .build())
                .requestTransformers(List.of((requestBuilder, body, ctx) -> {
                    throw new IllegalStateException("Model transformer failed");
                }))
                .build();
        final var agent = new TestAgent(agentSetup(model),
                                        List.of(new TransformerExtension(new ExtensionTransformer("x"))));

        final var response = execute(agent);

        assertEquals(ErrorType.REQUEST_TRANSFORM_FAILED, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(0, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    @SneakyThrows
    void pipelineWithoutTransformersSucceeds(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupMocks();
        final var model = baseModel(wiremock, null, null);
        final var agent = new TestAgent(agentSetup(model), List.of());

        final var response = execute(agent);

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals("Santanu", response.getData().username());
    }

    @Test
    @SneakyThrows
    void transformersApplyInOrderExtensionProviderModel(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        setupMocks();
        final var extensionTransformer = new ExtensionTransformer("extension|");
        final var providerTransformer = new MarkerTransformer("provider|");
        final var modelTransformer = new MarkerTransformer("model");
        final var model = baseModel(wiremock, providerTransformer, modelTransformer);
        final var agent = new TestAgent(agentSetup(model), List.of(new TransformerExtension(extensionTransformer)));

        final var response = execute(agent);

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1, extensionTransformer.calls.get());
        assertEquals(1, providerTransformer.calls.get());
        assertEquals(1, modelTransformer.calls.get());
        com.github.tomakehurst.wiremock.client.WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withRequestBody(matchingJsonPath("$[?(@.markers == 'extension|provider|model')]"))
                .withHeader("X-Extension-Transformer", equalTo("extension|"))
                .withHeader("X-Transformer", equalTo("model")));
    }

    private record OutputObject(
            String username,
            String message
    ) {
    }
}
