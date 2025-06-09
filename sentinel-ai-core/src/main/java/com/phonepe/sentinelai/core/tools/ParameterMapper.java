package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.primitives.Primitives;
import com.phonepe.sentinelai.core.utils.Pair;
import lombok.AllArgsConstructor;

import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static java.util.stream.Collectors.toMap;

/**
 *
 */
@AllArgsConstructor
public class ParameterMapper implements ExecutableToolVisitor<JsonNode> {
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode visit(ExternalTool externalTool) {
        return externalTool.getParameterSchema(); //Could be anything really, so we expect the toolbox to generate this
        // and keep it ready
    }

    @Override
    public JsonNode visit(InternalTool internalTool) {
        final var methodInfo = internalTool.getMethodInfo();
        return parametersFromMethodInfo(objectMapper, methodInfo);
    }

    public static ObjectNode parametersFromMethodInfo(final ObjectMapper objectMapper,
                                                      final ToolMethodInfo methodInfo) {
        final var paramNodes = methodInfo.parameters()
                .stream()
                .map(param -> {
                    final var rawType = param.getType().getRawClass();
                    final var paramSchema = (ObjectNode) schema(rawType);
                    if (rawType.isAssignableFrom(String.class) || Primitives.isWrapperType(rawType)) {
                        paramSchema.put("description", param.getDescription());
                    }
                    return Pair.of(param.getName(), paramSchema);
                })
                .collect(toMap(Pair::getFirst, Pair::getSecond));
        final var params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.put("additionalProperties", false);
        params.set("properties",
                   objectMapper.valueToTree(paramNodes));
        params.set("required",
                   objectMapper.valueToTree(paramNodes.keySet()));
        return params;
    }

}
