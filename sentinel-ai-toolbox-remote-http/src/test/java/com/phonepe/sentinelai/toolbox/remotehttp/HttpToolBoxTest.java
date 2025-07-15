package com.phonepe.sentinelai.toolbox.remotehttp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.tools.ExecutableToolVisitor;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link HttpToolBox}
 */
@WireMockTest
class HttpToolBoxTest {

    @Test
    void testToolCall(final WireMockRuntimeInfo wiremock) {
        stubFor(get(urlEqualTo("/api/v1/name"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "name" : "Santanu"
                                                         }
                                                         """, 200)));
        final var upstream = wiremock.getHttpBaseUrl();

        final var toolSource = new HttpToolSource() {
            @Override
            public HttpToolSource register(String upstream, List tool) {
                return this;
            }

            @Override
            public List<HttpToolMetadata> list(String upstream) {
                return List.of(HttpToolMetadata.builder()
                                       .name("getName")
                                       .description("Get name of the user")
                                       .parameters(Map.of())
                                       .build());
            }

            @Override
            public HttpCallSpec resolve(String upstream, String toolName, String arguments) {
                return HttpCallSpec.builder()
                        .method(HttpCallSpec.HttpMethod.GET)
                        .path("/api/v1/name")
                        .build();
            }

            @Override
            public List<String> upstreams() {
                return List.of("test");
            }
        };
        final var toolBox = new HttpToolBox(upstream,
                                            new OkHttpClient.Builder()
                                                          .build(),
                                            toolSource,
                                            JsonUtils.createMapper(),
                                                  url -> url);
        final var tools = toolBox.tools();
        final var toolId = AgentUtils.id(upstream, "getName");
        final var response = tools.get(toolId).accept(new ExecutableToolVisitor<String>() {
            @Override
            public String visit(ExternalTool externalTool) {
                return (String) externalTool.getCallable().apply(null, toolId, "").response();
            }

            @Override
            public String visit(InternalTool internalTool) {
                return "";
            }
        });
        assertEquals("{ \"name\" : \"Santanu\" }", response);
    }
}