package com.phonepe.sentinelai.toolbox.remotehttp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.phonepe.sentinelai.core.utils.AgentUtils.rootCause;
import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;

/**
 * A toolbox that exposes tools to make remote http calls using configuration
 */
@AllArgsConstructor
@Builder
@Slf4j
public class HttpToolBox implements ToolBox {
    private final String upstream;
    private final OkHttpClient httpClient;
    private final HttpToolSource<?, ?> httpToolSource;
    private final ObjectMapper mapper;
    private final UpstreamResolver upstreamResolver;
    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();

    public HttpToolBox(
            String upstream,
            OkHttpClient httpClient,
            HttpToolSource<?, ?> httpToolSource,
            ObjectMapper mapper,
            String endpointUrl) {
        this(upstream, httpClient, httpToolSource, mapper, UpstreamResolver.direct(endpointUrl));
    }

    @Override
    public String name() {
        return AgentUtils.id(upstream);
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        if (!knownTools.isEmpty()) {
            return knownTools;
        }
        log.debug("Loading tools for HTTP upstream {}", upstream);
        knownTools.putAll(httpToolSource.list(upstream)
                                  .stream()
                                  .map(tool -> {
                                      final var toolName = tool.getName();
                                      final var parameters = mapper.createObjectNode();
                                      final var paramNodes = mapper.createObjectNode()
                                              .put("type", "object")
                                              .put("additionalProperties", false)
                                              .set("properties", parameters);
                                      final var parameterMetadata = Objects.requireNonNullElseGet(tool.getParameters(),
                                                                                                  Map::<String,
                                                                                                          HttpToolMetadata.HttpToolParameterMeta>of);
                                      if (!parameterMetadata.isEmpty()) {
                                          parameterMetadata
                                                  .forEach((paramName, paramMeta) -> {
                                                      final var paramSchema = ((ObjectNode) schema(paramMeta.getType()
                                                                                                           .getRawType()))
                                                              .put("description", paramMeta.getDescription());

                                                      parameters.set(paramName, paramSchema);
                                                  });
                                          ((ObjectNode) paramNodes).set("required",
                                                                        mapper.valueToTree(parameterMetadata.keySet()));
                                      }
                                      return new ExternalTool(
                                              ToolDefinition.builder()
                                                      .id(AgentUtils.id(upstream, toolName))
                                                      .name(toolName)
                                                      .description(tool.getDescription())
                                                      .contextAware(false)
                                                      .strictSchema(true)
                                                      .build(),
                                              paramNodes,
                                              (toolId, arguments) -> {
                                                  final var toolDef = knownTools.get(toolId);
                                                  if (null == toolDef) {
                                                      throw new IllegalArgumentException("Unknown tool %s".formatted(
                                                              toolId));
                                                  }
                                                  final var resolved = httpToolSource.resolve(
                                                          upstream, toolDef.getToolDefinition().getName(), arguments);
                                                  return makeHttpCall(resolved);
                                              });
                                  })
                                  .collect(Collectors.toMap(tool -> tool.getToolDefinition().getId(),
                                                            Function.identity())));
        log.info("Loaded {} tools for HTTP upstream {}", knownTools.size(), upstream);
        return knownTools;
    }

    @SneakyThrows
    private ExternalTool.ExternalToolResponse makeHttpCall(final HttpCallSpec spec) {
        final var endpoint = upstreamResolver.resolve(upstream);
        final var requestBuilder = new Request.Builder()
                .url(URI.create("%s%s".formatted(endpoint, spec.getPath())).toURL());
        Objects.requireNonNullElse(spec.getHeaders(), Map.<String, List<String>>of())
                .forEach((name, values) -> values.forEach(value -> requestBuilder.header(name, value)));
        final var request = switch (spec.getMethod()) {
            case GET -> requestBuilder.get().build();
            case PUT -> requestBuilder.put(body(spec)).build();
            case POST -> requestBuilder.post(body(spec)).build();
            case HEAD -> requestBuilder.head().build();
            case DELETE -> requestBuilder.delete().build();
        };
        try (final var response = httpClient.newCall(request).execute()) {
            return new ExternalTool.ExternalToolResponse(body(spec, response),
                                                         !response.isSuccessful()
                                                         ? ErrorType.TOOL_CALL_TEMPORARY_FAILURE
                                                         : ErrorType.SUCCESS);
        }
        catch (IOException e) {
            return new ExternalTool.ExternalToolResponse("Error running tool: " + rootCause(e),
                                                         ErrorType.TOOL_CALL_TEMPORARY_FAILURE);
        }
    }

    @SneakyThrows
    private static String body(HttpCallSpec spec, Response response) {
        final var responseBody = response.body();
        if (null == responseBody) {
            return response.isSuccessful() ? "Successful" : "Failure";
        }
        final var bodyStr = response.body().string().trim().replaceAll("\\s+", " ");
        final var transformer = Objects.requireNonNullElseGet(spec.getResponseTransformer(),
                                                              UnaryOperator::<String>identity);
        return transformer.apply(bodyStr);
    }

    private static RequestBody body(HttpCallSpec spec) {
        if (!Strings.isNullOrEmpty(spec.getBody())) {
            final var contentType = Objects.requireNonNullElse(spec.getContentType(),
                                                               com.google.common.net.MediaType.JSON_UTF_8.type());
            return RequestBody.create(spec.getBody().getBytes(StandardCharsets.UTF_8),
                                      MediaType.parse(contentType));
        }
        throw new IllegalArgumentException("Body is null");
    }
}
