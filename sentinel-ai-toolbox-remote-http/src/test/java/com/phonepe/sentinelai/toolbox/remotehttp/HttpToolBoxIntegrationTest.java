package com.phonepe.sentinelai.toolbox.remotehttp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import wiremock.org.eclipse.jetty.http.HttpStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link HttpToolBox}
 */
@WireMockTest
@Slf4j
class HttpToolBoxIntegrationTest {
    public static class SimpleAgent extends Agent<String, String, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class, "Greet the user and Respond to user queries.", setup, extensions, tools);
        }

        @Override
        public String name() {
            return "simple-agent";
        }
    }

    @SneakyThrows
    @Test
    void test(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "tt", getClass());
        final var objectMapper = JsonUtils.createMapper();
        setupApiMocks();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                objectMapper
        );

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build();

        final var upstream = "test";
        HttpToolReaders.loadToolsFromYAML(
                Path.of(Objects.requireNonNull(getClass().getResource("/templates.yml")).toURI()),
                toolSource);
        final var toolBox = new HttpToolBox(upstream,
                                            okHttpClient,
                                            toolSource,
                                            objectMapper,
                                            wiremock.getHttpBaseUrl());
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();

        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(objectMapper)
                               .model(model)
                               .modelSettings(ModelSettings.builder()
                                                      .temperature(0f)
                                                      .seed(0)
                                                      .parallelToolCalls(false)
                                                      .build())
                               .build())
                .build()
                .registerToolbox(toolBox);
        final var response = agent.execute(AgentInput.<String>builder()
                                                   .requestMetadata(requestMetadata)
                                                   .request("How is the weather here?")
                                                   .build());
        log.info("Response: {}", response.getData());
        assertTrue(response.getData().contains("sunny"));
    }

    private static void setupApiMocks() {
        stubFor(get(urlEqualTo("/api/v1/name"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "name" : "santanu"
                                                         }
                                                         """, HttpStatus.OK_200)));
        stubFor(post(urlEqualTo("/api/v1/location"))
                        .withRequestBody(containing("santanu"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore"
                                                         }
                                                         """, 200)));
        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore",
                                                         "weatherValue" : "sunny"
                                                         }
                                                         """, 200)));
    }
}
