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
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.provider.RequestTransformerContext;
import com.phonepe.sentinelai.models.provider.SessionIdInjectionTransformer;
import com.phonepe.sentinelai.models.wire.WireContext;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link SessionIdInjectionTransformer}: header injection, JSON pointer body injection
 * with parent creation, the no-op cases and the fail-loud behavior on invalid pointers.
 */
@Slf4j
@WireMockTest
class SessionIdInjectionTransformerTest {

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

    private static AgentSetup agentSetup(final Model model) {
        return AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder().disableTools(true).build())
                .build();
    }

    private static ObjectNode body() {
        final var body = MAPPER.createObjectNode();
        body.put("model", "gpt-4o");
        return body;
    }

    private static RequestTransformerContext context(final String sessionId) {
        final var wireContext = WireContext.builder()
                .modelName("gpt-4o")
                .baseUrl("http://localhost")
                .tools(Map.of())
                .outputDefinitions(List.of())
                .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                .mapper(MAPPER)
                .build();
        return RequestTransformerContext.builder()
                .wireContext(wireContext)
                .sessionId(sessionId)
                .build();
    }

    @Test
    void emptyConfigurationIsNoOp() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder().build();
        final var builder = new Request.Builder().url("http://localhost");
        final var body = body();

        transformer.transform(builder, body, context("session-1"));

        assertNull(builder.build().header("x-session-id"));
        assertFalse(body.has("session_id"));
    }

    @Test
    void headerAndPointerBothApply() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .header("x-session-id")
                .bodyPath("/session_id")
                .build();
        final var builder = new Request.Builder().url("http://localhost");
        final var body = body();

        transformer.transform(builder, body, context("session-1"));

        assertEquals("session-1", builder.build().header("x-session-id"));
        assertEquals("session-1", body.path("session_id").asText());
    }

    @Test
    void headerOnlyInjectsHeader() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .header("x-session-id")
                .build();
        final var builder = new Request.Builder().url("http://localhost");
        final var body = body();

        transformer.transform(builder, body, context("session-1"));

        assertEquals("session-1", builder.build().header("x-session-id"));
        assertFalse(body.has("session_id"));
    }

    @Test
    void invalidPointerFailsLoud() {
        final var transformer = SessionIdInjectionTransformer.builder()
                .bodyPath("session_id")
                .build();
        final var body = body();

        // Path "session_id" is not a pointer; it lands on the whole body and must fail.
        assertThrows(IllegalArgumentException.class,
                     () -> transformer.transform(new Request.Builder().url("http://localhost"),
                                                 body,
                                                 context("session-1")));
    }

    @Test
    void nestedPointerCreatesMissingParents() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .bodyPath("/metadata/session_id")
                .build();
        final var body = body();

        transformer.transform(new Request.Builder().url("http://localhost"), body, context("session-1"));

        assertEquals("session-1", body.path("metadata").path("session_id").asText());
    }

    @Test
    void noSessionIdOnRunAddsNothing() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .header("x-session-id")
                .bodyPath("/session_id")
                .build();
        final var builder = new Request.Builder().url("http://localhost");
        final var body = body();

        transformer.transform(builder, body, context(null));

        assertNull(builder.build().header("x-session-id"));
        assertFalse(body.has("session_id"));
    }

    @Test
    void pointerThroughScalarFailsLoud() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .bodyPath("/model/session_id")
                .build();
        final var body = body();

        // /model is the string "gpt-4o"; a pointer through it must fail.
        assertThrows(IllegalArgumentException.class,
                     () -> transformer.transform(new Request.Builder().url("http://localhost"),
                                                 body,
                                                 context("session-1")));
    }

    @Test
    void topLevelPointerInjectsBodyField() throws Exception {
        final var transformer = SessionIdInjectionTransformer.builder()
                .bodyPath("/session_id")
                .build();
        final var builder = new Request.Builder().url("http://localhost");
        final var body = body();

        transformer.transform(builder, body, context("session-1"));

        assertEquals("session-1", body.path("session_id").asText());
        assertEquals("gpt-4o", body.path("model").asText());
        assertNull(builder.build().header("x-session-id"));
    }

    @Test
    @SneakyThrows
    void wiremockRoundTripCarriesHeaderAndBody(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", SessionIdInjectionTransformerTest.class);
        final Model model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .requestTransformers(List.of(SessionIdInjectionTransformer.builder()
                                .header("x-session-id")
                                .bodyPath("/metadata/session_id")
                                .build()))
                        .build())
                .build();
        final var agent = new TestAgent(agentSetup(model));

        final AgentOutput<OutputObject> response = agent.execute(
                                                                 AgentInput.<OutputObject>builder()
                                                                         .request(new OutputObject(null, "Hi"))
                                                                         .requestMetadata(AgentRequestMetadata.builder()
                                                                                 .sessionId("session-1").build())
                                                                         .build());

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("x-session-id", equalTo("session-1"))
                .withRequestBody(matchingJsonPath("$.metadata.session_id", equalTo("session-1"))));
    }

    private record OutputObject(
            String username,
            String message
    ) {
    }
}
