package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        final var paramNodes = internalTool.getParameters()
                .values()
                .stream()
                .map(param -> Pair.of(param.getName(),
                                      schema(param.getType().getRawClass())))
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
