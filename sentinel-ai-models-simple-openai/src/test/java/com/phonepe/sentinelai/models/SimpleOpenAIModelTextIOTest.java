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
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.NonNull;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

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

        public TestAgent(
                @NonNull AgentSetup setup) {
            super(String.class,
                  "Greet the user",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }

        @Tool("Get name of the user")
        public String getName() {
            return "Santanu";
        }
    }

    @Test
    void testAgent(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(2, "textio", getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
                        .baseUrl(TestUtils.getTestProperty("AZURE_ENDPOINT", wiremock.getHttpBaseUrl()))
                        .apiKey(TestUtils.getTestProperty("AZURE_API_KEY", "BLAH"))
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                objectMapper
        );
        final var agent = new TestAgent(AgentSetup.builder()
                                                .model(model)
                                                .mapper(objectMapper)
                                                .modelSettings(ModelSettings.builder()
                                                                       .temperature(0.1f)
                                                                       .seed(1)
                                                                       .build())
                                                .build());
        final var response = agent.execute(AgentInput.<String>builder()
                                                   .request("Hi")
                                                   .requestMetadata(
                                                           AgentRequestMetadata.builder()
                                                                   .sessionId("s1")
                                                                   .userId("ss").build())
                                                   .build());
        assertTrue(response.getData().contains("Santanu"));
        assertTrue(response.getUsage().getTotalTokens() > 1);
        ensureOutputGenerated(response);
    }
}
