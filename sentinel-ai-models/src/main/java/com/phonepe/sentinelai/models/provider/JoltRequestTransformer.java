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

package com.phonepe.sentinelai.models.provider;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.exception.JoltException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.SneakyThrows;
import lombok.Value;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link RequestTransformer} that applies a chain of Jolt operations to the request body. Each
 * {@link JoltTransform} pairs a Jolt operation name with its spec map. Operations apply in list
 * order; each operation sees the output of the previous one.
 *
 * <p>Use this for vendor-specific payload fields that no protocol setting covers (for example
 * {@code chat_template_kwargs.thinking = false} for open-weight servers). Declarative extras stay
 * available through {@code ModelOptions.extras}.
 *
 * <p>Create the transformer from a typed transform list, a JSON string, or a JSON node:
 * <pre>
 * JoltRequestTransformer.ofTransforms(List.of(
 * JoltTransform.builder()
 * .operation("default")
 * .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
 * .build()))
 * JoltRequestTransformer.fromJson(
 * "[{\"operation\": \"default\","
 * + " \"spec\": {\"chat_template_kwargs\": {\"thinking\": false}}}]")
 * </pre>
 */
@Value
public class JoltRequestTransformer implements RequestTransformer {

    private static final ObjectMapper FACTORY_MAPPER = new ObjectMapper();

    /**
     * Builder for the transformer; see {@link #builder()}.
     */
    public static class JoltRequestTransformerBuilder {

        private final List<JoltTransform> transforms = new ArrayList<>();

        /**
         * Builds the transformer from the added transforms.
         *
         * @return New transformer.
         */
        public JoltRequestTransformer build() {
            return new JoltRequestTransformer(transforms);
        }

        /**
         * Adds one Jolt transform to the chain.
         *
         * @param transform Jolt transform to append.
         * @return This builder.
         */
        public JoltRequestTransformerBuilder transform(final JoltTransform transform) {
            transforms.add(transform);
            return this;
        }

        /**
         * Adds one Jolt operation with its spec map to the chain.
         *
         * @param operation Jolt operation name.
         * @param spec      Jolt spec map of the operation.
         * @return This builder.
         */
        public JoltRequestTransformerBuilder transform(final String operation, final Map<String, Object> spec) {
            return transform(JoltTransform.builder().operation(operation).spec(spec).build());
        }
    }

    List<JoltTransform> transforms;

    Chainr chainr;

    private JoltRequestTransformer(final List<JoltTransform> transforms) {
        this.transforms = List.copyOf(transforms);
        if (this.transforms.isEmpty()) {
            this.chainr = null;
            return;
        }
        try {
            this.chainr = Chainr.fromSpec(toChainrSpec(this.transforms));
        }
        catch (final JoltException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Returns a builder for the transformer.
     *
     * @return A new builder.
     */
    public static JoltRequestTransformerBuilder builder() {
        return new JoltRequestTransformerBuilder();
    }

    /**
     * Creates the transformer from a JSON array of transform objects. Each element carries the
     * fields {@code operation} and {@code spec}; see {@link JoltTransform}.
     *
     * @param json JSON array of transforms, for example from a config file.
     * @return New transformer.
     * @throws IllegalArgumentException when the JSON is not a JSON array of valid transforms.
     */
    public static JoltRequestTransformer fromJson(final String json) {
        return fromJsonNode(parseJson(json));
    }

    /**
     * Creates the transformer from a JSON node holding an array of transform objects. Each
     * element carries the fields {@code operation} and {@code spec}; see {@link JoltTransform}.
     *
     * @param node JSON array of transforms, for example a parsed config value.
     * @return New transformer.
     * @throws IllegalArgumentException when the node is not an array of valid transforms.
     */
    public static JoltRequestTransformer fromJsonNode(final JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("Jolt transform config must be a JSON array");
        }
        final var transforms = new ArrayList<JoltTransform>(node.size());
        for (final var element : node) {
            transforms.add(fromJsonNodeToObject(element));
        }
        return new JoltRequestTransformer(transforms);
    }

    /**
     * Creates the transformer from the given transforms.
     *
     * @param transforms Jolt transforms to apply, in order; may be empty.
     * @return New transformer.
     */
    public static JoltRequestTransformer ofTransforms(final List<JoltTransform> transforms) {
        return new JoltRequestTransformer(transforms);
    }

    private static JoltTransform fromJsonNodeToObject(final JsonNode node) {
        return FACTORY_MAPPER.convertValue(node, JoltTransform.class);
    }

    @SneakyThrows(JsonProcessingException.class)
    private static JsonNode parseJson(final String json) {
        return FACTORY_MAPPER.readTree(json);
    }

    private static List<Map<String, Object>> toChainrSpec(final List<JoltTransform> transforms) {
        return transforms.stream()
                .<Map<String, Object>>map(transform -> Map.of(
                                                              "operation",
                                                              transform.getOperation(),
                                                              "spec",
                                                              transform.getSpec()))
                .toList();
    }

    @Override
    public void transform(final Request.Builder requestBuilder,
                          final ObjectNode body,
                          final RequestTransformerContext ctx) {
        if (transforms.isEmpty()) {
            return;
        }
        final var mapper = ctx.mapper();
        final Object input = mapper.convertValue(body, Object.class);
        final Object output = chainr.transform(input);
        if (!(output instanceof Map)) {
            throw new IllegalArgumentException("Jolt transform must produce a JSON object but produced: "
                    + output.getClass().getSimpleName());
        }
        final var result = mapper.convertValue(output, ObjectNode.class);
        body.removeAll();
        body.setAll(result);
    }
}
