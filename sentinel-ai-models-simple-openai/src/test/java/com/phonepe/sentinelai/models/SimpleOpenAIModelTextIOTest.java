package com.phonepe.sentinelai.models;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.Agent;
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
        final var model = new SimpleOpenAIModel(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
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
        final var response = agent.execute("Hi", AgentRequestMetadata.builder()
                              .sessionId("s1")
                              .userId("ss").build(),
                      null,
                      null);
        assertTrue(response.getData().contains("Santanu"));
    }
}
