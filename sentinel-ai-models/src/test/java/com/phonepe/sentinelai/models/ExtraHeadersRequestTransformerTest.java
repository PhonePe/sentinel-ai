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
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.ExtraHeadersRequestTransformer;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.provider.RequestTransformerContext;
import com.phonepe.sentinelai.models.wire.WireContext;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link ExtraHeadersRequestTransformer}: header
 * application on the request builder, the empty-configuration no-op and the WireMock round trip.
 */
@Slf4j
@WireMockTest
class ExtraHeadersRequestTransformerTest {

    private static final ObjectMapper MAPPER = JsonUtils.createMapper();

    private static final ObjectNode BODY = MAPPER.createObjectNode();

    private static final RequestTransformerContext CONTEXT = RequestTransformerContext
            .builder()
            .wireContext(WireContext.builder()
                    .modelName("gpt-4o")
                    .baseUrl("http://localhost")
                    .tools(Map.of())
                    .outputDefinitions(List.of())
                    .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
                    .mapper(MAPPER)
                    .build())
            .build();

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

    @Test
    void appliesConfiguredHeadersToRequestBuilder() {
        final var transformer = ExtraHeadersRequestTransformer.builder()
                .header("x-gateway-tenant", "acme")
                .header("x-api-version", "2")
                .build();
        final var builder = new Request.Builder().url("http://localhost");

        transformer.transform(builder, BODY, CONTEXT);

        final var request = builder.build();
        assertEquals("acme", request.header("x-gateway-tenant"));
        assertEquals("2", request.header("x-api-version"));
    }

    @Test
    void emptyHeaderMapIsNoOp() throws Exception {
        final var transformer = ExtraHeadersRequestTransformer.builder()
                .build();
        final var builder = new Request.Builder().url("http://localhost");

        transformer.transform(builder, BODY, CONTEXT);

        assertFalse(builder.build().headers().names().contains("x-gateway-tenant"));
    }

    @Test
    @SneakyThrows
    void wiremockRoundTripCarriesHeaders(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocks(1, "no-tools", ExtraHeadersRequestTransformerTest.class);
        final Model model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .requestTransformers(List.of(
                                                     ExtraHeadersRequestTransformer
                                                             .builder()
                                                             .header("x-gateway-tenant", "acme")
                                                             .build()))
                        .build())
                .build();
        final var agent = new TestAgent(agentSetup(model));

        final AgentOutput<OutputObject> response = agent.execute(
                                                                 AgentInput.<OutputObject>builder().request(
                                                                                                            new OutputObject(null,
                                                                                                                             "Hi"))
                                                                         .build());

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertTrue(response.getData().username().length() > 0);
        WireMock.verify(postRequestedFor(urlEqualTo(TestStubs.ENDPOINT))
                .withHeader("x-gateway-tenant", equalTo("acme")));
    }

    private record OutputObject(
            String username,
            String message
    ) {
    }
}
