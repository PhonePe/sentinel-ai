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

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;

import lombok.NonNull;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.phonepe.sentinelai.core.utils.TestUtils.ensureOutputGenerated;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests simple text based io with {@link SimpleOpenAIModel}
 */
@WireMockTest
class SimpleOpenAIModelTextIOTest {
    private static final class TestAgent extends Agent<String, String, TestAgent> {

        public TestAgent(@NonNull AgentSetup setup) {
            super(String.class,
                  "Greet the user. Before doing a step keep letting me know one of the course of action you are going to take. You can generate output multiple times.",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Tool("Get name of the user")
        public String getName() {
            return "Santanu";
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    @Test
    void testAgent(final WireMockRuntimeInfo wiremock) {
        testAgentSyncInternal(wiremock, 3, "textio", "Hi.", OutputGenerationMode.TOOL_BASED);
    }

    @Test
    void testAgentStructuredOutput(final WireMockRuntimeInfo wiremock) {
        testAgentSyncInternal(wiremock, 2, "textio-so", "Hi.", OutputGenerationMode.STRUCTURED_OUTPUT);
    }

    @Test
    void testAgentMultiTurn(final WireMockRuntimeInfo wiremock) {
        testAgentSyncInternal(wiremock,
                              4,
                              "textio-mt",
                              "Hi. Tell me a poem. Have my name in the greeting message. Fist provide a plan for this and then the poem",
                              OutputGenerationMode.TOOL_BASED);
    }

    @Test
    void testAgentMultiTurnSO(final WireMockRuntimeInfo wiremock) {
        //This isn't actually multi turn
        testAgentSyncInternal(wiremock,
                              2,
                              "textio-so-mt",
                              "Hi. Tell me a poem. Have my name in the greeting message. Fist provide a plan for this and then the poem",
                              OutputGenerationMode.STRUCTURED_OUTPUT);
    }

    void testAgentSyncInternal(final WireMockRuntimeInfo wiremock,
                               int turnCount,
                               String mockName,
                               String prompt,
                               OutputGenerationMode outputGenerationMode) {
        TestUtils.setupMocks(turnCount, mockName, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var httpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ofSeconds(20))
                .build();
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(httpClient))
                                                          .build(),
                                                  objectMapper);
        final var agent = new TestAgent(AgentSetup.builder()
                .model(model)
                .mapper(objectMapper)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .seed(1)
                        .build())
                .outputGenerationMode(outputGenerationMode)
                .build());
        final var response = agent.execute(AgentInput.<String>builder()
                .request(prompt)
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("ss")
                        .build())
                .build());
        assertTrue(response.getData().contains("Santanu"));
        assertTrue(response.getUsage().getTotalTokens() > 1);
        // ensureOutputGenerated(response);
    }
}
