package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.tools.ExecutableToolVisitor;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.*;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.STRING;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.textSubstitutor;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link HttpCallTemplateExpander}
 */
@WireMockTest
class HttpCallTemplateExpanderTest {

    @Test
    void test(WireMockRuntimeInfo wiremock) {
        final var mapper = JsonUtils.createMapper();
        stubFor(post(urlEqualTo("/api/v1/location"))
                        .withRequestBody(containing("santanu"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore"
                                                         }
                                                         """, 200)));

        final var upstream = wiremock.getHttpBaseUrl();
        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(mapper)
                .build()
                .register(upstream,
                          TemplatizedHttpTool.builder()
                                  .metadata(HttpToolMetadata.builder()
                                                      .name("getLocation")
                                                      .description("Get location of the user")
                                                      .parameters(
                                                              Map.of("name",
                                                                     new HttpToolMetadata.HttpToolParameterMeta(
                                                                             "Name of the user", STRING)))
                                                      .build())
                                  .template(HttpCallTemplate.builder()
                                                    .path(text("/api/v1/location"))
                                                    .method(HttpCallSpec.HttpMethod.POST)
                                                    .body(textSubstitutor("{ \"name\" : \"${name}\" }"))
                                                    .build())
                                  .responseTransformations(ResponseTransformerConfig.builder()
                                                                   .type(ResponseTransformerConfig.Type.JOLT)
                                                                   .config("""
                                                                            [
                                                                              {
                                                                                 "operation": "shift",
                                                                                 "spec": {
                                                                                    "location": "userLocation"
                                                                                 }
                                                                              }
                                                                            ]
                                                                            """)
                                                                   .build())
                                  .build());

        final var toolBox = new HttpToolBox(upstream,
                                            new OkHttpClient.Builder()
                                                          .build(),
                                            toolSource,
                                            JsonUtils.createMapper(),
                                                  url -> upstream);
        final var tools = toolBox.tools();
        final var toolId = AgentUtils.id(upstream, "getLocation");
        final var response = tools.get(toolId)
                .accept(new ExecutableToolVisitor<String>() {
                    @Override
                    public String visit(ExternalTool externalTool) {
                        return (String) externalTool.getCallable().apply(toolId, """
                                {
                                    "name" : "santanu"
                                }
                                """).response();
                    }

                    @Override
                    public String visit(InternalTool internalTool) {
                        return "";
                    }
                });
        assertEquals("{\"userLocation\":\"Bangalore\"}", response);
    }
}