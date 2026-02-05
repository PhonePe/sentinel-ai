package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.tools.ExecutableToolVisitor;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.handlebar.HandlebarUtil;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType.STRING;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.text;
import static com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate.Template.textSubstitutor;
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

        final var upstream = TestUtils.getTestProperty("REMOTE_HTTP_ENDPOINT", wiremock.getHttpBaseUrl());
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
                        return (String) externalTool.getCallable().apply(null,
                                toolId, """
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

    @Test
    @SneakyThrows
    void testHandlebar(WireMockRuntimeInfo wiremock) {
        final var mapper = JsonUtils.createMapper();
        stubFor(post(urlEqualTo("/api/v1/location"))
                .withRequestBody(containing("santanu"))
                .willReturn(jsonResponse("""
                        {
                        "location" : "Bangalore"
                        }
                        """, 200)));
        stubFor(post(urlEqualTo("/api/v1/device"))
                .withRequestBody(containing("santanu"))
                .willReturn(jsonResponse("""
                        {
                        "device" : "android"
                        }
                        """, 200)));
        stubFor(post(urlEqualTo("/api/v1/device"))
                .withRequestBody(containing("santanu.sinha"))
                .willReturn(jsonResponse("""
                        {
                        "device" : "ios"
                        }
                        """, 200)));

        final var upstream = TestUtils.getTestProperty("REMOTE_HTTP_ENDPOINT", wiremock.getHttpBaseUrl());

        // reading handlebar template from file
        final var handlebarFileContent = Files.readString(Paths.get(getClass().getClassLoader().getResource("templates/test.hbs").toURI()));

        // reading through helper
        HandlebarUtil.registerHelper("getName", (context, options) -> getName(context.toString()));
        final var templateString = """
                {{{getName name}}}
                """;

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
                                        .body(HttpCallTemplate.Template.handlebars(handlebarFileContent))
                                        .method(HttpCallSpec.HttpMethod.POST)
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
                                .build(),
                        TemplatizedHttpTool.builder()
                                .metadata(HttpToolMetadata.builder()
                                        .name("getDevice")
                                        .description("Get device of the user")
                                        .parameters(
                                                Map.of("name",
                                                        new HttpToolMetadata.HttpToolParameterMeta(
                                                                "Device of the user", STRING)))
                                        .build())
                                .template(HttpCallTemplate.builder()
                                        .path(text("/api/v1/device"))
                                        .body(HttpCallTemplate.Template.handlebars(handlebarFileContent))
                                        .method(HttpCallSpec.HttpMethod.POST)
                                        .build())
                                .responseTransformations(ResponseTransformerConfig.builder()
                                        .type(ResponseTransformerConfig.Type.JOLT)
                                        .config("""
                                                [
                                                  {
                                                     "operation": "shift",
                                                     "spec": {
                                                        "device": "userDevice"
                                                     }
                                                  }
                                                ]
                                                """)
                                        .build()).build(),
                        TemplatizedHttpTool.builder()
                                .metadata(HttpToolMetadata.builder()
                                        .name("getDevice2")
                                        .description("Get device of the user")
                                        .parameters(
                                                Map.of("name",
                                                        new HttpToolMetadata.HttpToolParameterMeta(
                                                                "Device of the user", STRING)))
                                        .build())
                                .template(HttpCallTemplate.builder()
                                        .path(text("/api/v1/device"))
                                        .body(HttpCallTemplate.Template.handlebars(templateString))
                                        .method(HttpCallSpec.HttpMethod.POST)
                                        .build())
                                .responseTransformations(ResponseTransformerConfig.builder()
                                        .type(ResponseTransformerConfig.Type.JOLT)
                                        .config("""
                                                [
                                                  {
                                                     "operation": "shift",
                                                     "spec": {
                                                        "device": "userDevice"
                                                     }
                                                  }
                                                ]
                                                """)
                                        .build()).build());

        final var toolBox = new HttpToolBox(upstream,
                new OkHttpClient.Builder()
                        .build(),
                toolSource,
                JsonUtils.createMapper(),
                url -> upstream);
        final var tools = toolBox.tools();
        final var locationToolId = AgentUtils.id(upstream, "getLocation");
        final var deviceToolId = AgentUtils.id(upstream, "getDevice");
        final var device2ToolId = AgentUtils.id(upstream, "getDevice2");

        final var response1 = tools.get(locationToolId)
                .accept(new ExecutableToolVisitor<String>() {
                    @Override
                    public String visit(ExternalTool externalTool) {
                        return (String) externalTool.getCallable().apply(null,
                                locationToolId, """
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
        final var response2 = tools.get(deviceToolId)
                .accept(new ExecutableToolVisitor<String>() {
                    @Override
                    public String visit(ExternalTool externalTool) {
                        return (String) externalTool.getCallable().apply(null,
                                deviceToolId, """
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
        final var response3 = tools.get(device2ToolId)
                .accept(new ExecutableToolVisitor<String>() {
                    @Override
                    public String visit(ExternalTool externalTool) {
                        return (String) externalTool.getCallable().apply(null,
                                device2ToolId, """
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
        assertEquals("{\"userLocation\":\"Bangalore\"}", response1);
        assertEquals("{\"userDevice\":\"android\"}", response2);
        assertEquals("{\"userDevice\":\"ios\"}", response3);
    }

    private String getName(final String context) {
        return "{" +
                "\"name\": \"" + context + ".sinha\""+
                "}";
    }
}