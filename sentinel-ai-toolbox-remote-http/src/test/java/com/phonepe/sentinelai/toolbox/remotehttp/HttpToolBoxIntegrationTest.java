package com.phonepe.sentinelai.toolbox.remotehttp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import wiremock.org.eclipse.jetty.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.STRING;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.strSubstitutor;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
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

    @Test
    void test(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "tt", getClass());
        final var objectMapper = JsonUtils.createMapper();
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
                                                         "location" : "Sunny"
                                                         }
                                                         """, 200)));
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder()
                                                                       .callTimeout(Duration.ofSeconds(180))
                                                                       .connectTimeout(Duration.ofSeconds(120))
                                                                       .readTimeout(Duration.ofSeconds(180))
                                                                       .writeTimeout(Duration.ofSeconds(120))
                                                                       .build()))
                        .build(),
                objectMapper
        );

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
                .build();

        final var upstream = "test";
        toolSource.register(upstream, List.of(
                HttpTool.builder()
                        .toolConfig(HttpToolMetadata.builder()
                                            .name("getName")
                                            .description("Get name of the user")
                                            .build())
                        .template(HttpCallTemplate.builder()
                                          .path(text("/api/v1/name"))
                                          .method(HttpRemoteCallSpec.HttpMethod.GET)
                                          .build())
                        .build(),
                HttpTool.builder()
                        .toolConfig(HttpToolMetadata.builder()
                                            .name("getLocation")
                                            .description("Get location of the user")
                                            .parameters(
                                                    Map.of("name",
                                                           new HttpToolMetadata.HttpToolParameterMeta(
                                                                   "Name of the user", STRING)))
                                            .build())
                        .template(HttpCallTemplate.builder()
                                          .path(strSubstitutor("/api/v1/location"))
                                          .method(HttpRemoteCallSpec.HttpMethod.POST)
                                          .body(strSubstitutor("{ \"name\" : \"${name}\" }"))
                                          .build())
                        .build(),
                HttpTool.builder()
                        .toolConfig(HttpToolMetadata.builder()
                                            .name("getWeatherForCity")
                                            .description("Get weather for a particular location")
                                            .parameters(
                                                    Map.of("city",
                                                           new HttpToolMetadata.HttpToolParameterMeta(
                                                                   "Name of the city", STRING)))
                                            .build())
                        .template(HttpCallTemplate.builder()
                                          .path(strSubstitutor("/api/v1/weather/${city}"))
                                          .method(HttpRemoteCallSpec.HttpMethod.GET)
                                          .build())
                        .build()
                                             ));

        final var toolBox = new HttpToolBox(upstream,
                                            new OkHttpClient.Builder()
                                                          .build(),
                                            toolSource,
                                            JsonUtils.createMapper(),
                                                  url -> wiremock.getHttpBaseUrl());
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
        assertTrue(response.getData().contains("sunny"));
    }
}
