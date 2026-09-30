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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.JoltRequestTransformer;
import com.phonepe.sentinelai.models.provider.JoltTransform;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.wire.WireContext;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link JoltRequestTransformer}: the Jolt semantics of the transform chain, the in-place
 * body mutation contract, the JSON factories and the fail-loud behavior of invalid specs.
 */
@WireMockTest
class JoltRequestTransformerTest {

    private static final ObjectMapper MAPPER = JsonUtils.createMapper();

    private static final class TestAgent extends Agent<OutputObject, OutputObject, TestAgent> {

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

    private static ObjectNode body(final String json) {
        return (ObjectNode) MAPPER.valueToTree(Map.of("greeting", "hello"));
    }

    private static WireContext context() {
        return WireContext.builder()
                .modelName("gpt-4o")
                .baseUrl("http://localhost")
                .tools(Map.of())
                .outputDefinitions(List.of())
                .outputGenerationMode(com.phonepe.sentinelai.core.model.OutputGenerationMode.TOOL_BASED)
                .mapper(MAPPER)
                .build();
    }

    @Test
    void builderCollectsTransformsInOrder() {
        final var transformer = JoltRequestTransformer.builder()
                .transform("default", Map.of("first", 1))
                .transform(JoltTransform.builder()
                        .operation("default")
                        .spec(Map.of("second", 2))
                        .build())
                .build();

        assertEquals(2, transformer.getTransforms().size());
        assertEquals("default", transformer.getTransforms().get(1).getOperation());
        assertEquals(Map.of("second", 2), transformer.getTransforms().get(1).getSpec());
    }

    @Test
    void defaultOperationAddsNestedFieldWithoutOverwriting() throws Exception {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of(JoltTransform.builder()
                .operation("default")
                .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
                .build()));
        final var body = body("{}");
        body.put("model", "qwen3");

        transformer.transform(new okhttp3.Request.Builder().url("http://localhost"), body, context());

        assertFalse(body.path("chat_template_kwargs").path("thinking").asBoolean(true));
        assertEquals("qwen3", body.path("model").asText());
        assertEquals("hello", body.path("greeting").asText());
    }

    @Test
    void defaultOperationKeepsExistingValue() throws Exception {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of(JoltTransform.builder()
                .operation("default")
                .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
                .build()));
        final var body = body("{}");
        body.putObject("chat_template_kwargs").put("thinking", true);

        transformer.transform(new okhttp3.Request.Builder().url("http://localhost"), body, context());

        assertTrue(body.path("chat_template_kwargs").path("thinking").asBoolean());
    }

    @Test
    void emptyTransformListLeavesBodyUnchanged() throws Exception {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of());
        final var body = body("{}");

        transformer.transform(new okhttp3.Request.Builder().url("http://localhost"), body, context());

        assertEquals("hello", body.path("greeting").asText());
        assertEquals(1, body.size());
    }

    @Test
    void fromJsonLoadsTransforms() {
        final var transformer = JoltRequestTransformer.fromJson("""
                [
                  {"operation": "default", "spec": {"chat_template_kwargs": {"thinking": false}}}
                ]""");

        assertEquals(1, transformer.getTransforms().size());
        assertEquals("default", transformer.getTransforms().get(0).getOperation());
    }

    @Test
    void fromJsonNodeLoadsTransforms() {
        final var node = MAPPER.valueToTree(List.of(
                                                    Map.of("operation", "default", "spec", Map.of("added", 1))));

        final var transformer = JoltRequestTransformer.fromJsonNode(node);

        assertEquals(1, transformer.getTransforms().size());
        assertEquals(Map.of("added", 1), transformer.getTransforms().get(0).getSpec());
    }

    @Test
    void invalidSpecFailsLoudAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                     () -> JoltRequestTransformer.ofTransforms(List.of(JoltTransform.builder()
                             .operation("no-such-jolt-operation")
                             .spec(Map.of())
                             .build())));
    }


    @Test
    void rootShiftingSpecFailsLoud() {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of(JoltTransform.builder()
                .operation("shift")
                .spec(Map.of("*", "[]"))
                .build()));
        final var body = body("{}");

        assertThrows(IllegalArgumentException.class,
                     () -> transformer.transform(new okhttp3.Request.Builder().url("http://localhost"),
                                                 body,
                                                 context()));
    }

    @Test
    void transformMutatesTheGivenBodyInstance() throws Exception {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of(JoltTransform.builder()
                .operation("default")
                .spec(Map.of("added", "value"))
                .build()));
        final var body = body("{}");

        transformer.transform(new okhttp3.Request.Builder().url("http://localhost"), body, context());

        assertSame(body, body);
        assertEquals("value", body.path("added").asText());
        assertEquals("hello", body.path("greeting").asText());
    }

    @Test
    void transformsApplyInListOrder() throws Exception {
        final var transformer = JoltRequestTransformer.ofTransforms(List.of(
                                                                            JoltTransform.builder()
                                                                                    .operation("shift")
                                                                                    .spec(Map.of("greeting",
                                                                                                 "salutation"))
                                                                                    .build(),
                                                                            JoltTransform.builder()
                                                                                    .operation("default")
                                                                                    .spec(Map.of("salutation", "hi"))
                                                                                    .build()));
        final var body = body("{}");

        transformer.transform(new okhttp3.Request.Builder().url("http://localhost"), body, context());

        assertFalse(body.has("greeting"));
        assertEquals("hello", body.path("salutation").asText());
    }

    @Test
    void wiremockInvalidSpecAbortsAsRequestTransformFailed(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", JoltRequestTransformerTest.class);
        final var chain = JoltRequestTransformer.builder()
                .transform("default", Map.of("chat_template_kwargs", Map.of("thinking", false)))
                .build();
        final var failing = new com.phonepe.sentinelai.models.provider.RequestTransformer() {
            @Override
            public void transform(final okhttp3.Request.Builder requestBuilder,
                                  final ObjectNode body,
                                  final com.phonepe.sentinelai.models.wire.WireContext ctx) {
                throw new IllegalArgumentException("Jolt transform failed: broken spec");
            }
        };
        final Model model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .requestTransformers(List.of(chain, failing))
                        .build())
                .build();
        final var agent = new TestAgent(AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());
        final var response = agent.execute(
                                           AgentInput.<OutputObject>builder().request(new OutputObject(null, "Hi"))
                                                   .build());

        assertEquals(ErrorType.REQUEST_TRANSFORM_FAILED, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(0, postRequestedFor(urlEqualTo(TestStubs.ENDPOINT)));
    }

    @Test
    void wiremockRoundTripDeliversTransformedBody(final WireMockRuntimeInfo wiremock) throws Exception {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", JoltRequestTransformerTest.class);
        final Model model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .requestTransformers(List.of(JoltRequestTransformer.builder()
                                .transform("default",
                                           Map.of("chat_template_kwargs", Map.of("thinking", false)))
                                .build()))
                        .build())
                .build();
        final var agent = new TestAgent(AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build());
        final var response = agent.execute(
                                           AgentInput.<OutputObject>builder().request(new OutputObject(null, "Hi"))
                                                   .build());

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        com.github.tomakehurst.wiremock.client.WireMock.verify(
                                                               postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                                                                       .withRequestBody(matchingJsonPath(
                                                                                                         "$.chat_template_kwargs[?(@.thinking == false)]")));
    }

    private record OutputObject(
            String username,
            String message
    ) {
    }
}
