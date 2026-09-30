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

package com.phonepe.sentinelai.models.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.model.transformer.RequestTransformer;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.models.ModelOptions;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Neutral inputs a wire protocol needs to build one request. The model loop constructs this once
 * per turn. Everything here is provider-agnostic; protocol specifics (endpoint shapes, field
 * names) live in the {@link WireProtocol}.
 */
@Value
@Builder(toBuilder = true)
public class WireContext {

    /**
     * Name of the model to call.
     */
    @NonNull
    String modelName;

    /**
     * Base URL of the provider endpoint.
     */
    @NonNull
    String baseUrl;

    /**
     * User id for this run; null when not provided.
     */
    String userId;

    /**
     * Id of the run that produces this request; null when not provided.
     */
    String runId;

    /**
     * Model settings (temperature, max tokens, penalties, reasoning, ...); may be null.
     */
    ModelSettings modelSettings;

    /**
     * Tools available for this call; may be empty.
     */
    @NonNull
    Map<String, ExecutableTool> tools;

    /**
     * Output definitions when the run wants structured output; may be empty.
     */
    @NonNull
    List<ModelOutputDefinition> outputDefinitions;

    /**
     * Neutral compliant JSON schema built from the output definitions. Protocols use it for
     * structured output request fields (for example response_format) when the run mode is
     * STRUCTURED_OUTPUT.
     */
    ObjectNode outputSchema;

    /**
     * Output generation mode of the run.
     */
    @NonNull
    OutputGenerationMode outputGenerationMode;

    /**
     * Free-form extras JSON node (see ModelOptions). Merged into the request body last by
     * {@link WireProtocol#applyExtras}; may be null.
     */
    JsonNode extras;

    /**
     * True when the model call streams the response over server-sent events. Protocols use it
     * to request a stream from the provider (for example body field {@code stream}). Defaults
     * to false.
     */
    @Builder.Default
    boolean streaming = false;

    /**
     * Model id sent in the request body. When null, {@link #modelName} is sent.
     */
    String modelId;

    /**
     * Tool choice policy of the model options; drives the request tool choice field.
     */
    @Builder.Default
    ModelOptions.ToolChoice toolChoice = ModelOptions.DEFAULT_TOOL_CHOICE;

    /**
     * Jackson mapper of the run. Comes from the agent setup; protocols use it for all JSON
     * work so they stay stateless.
     */
    @NonNull
    ObjectMapper mapper;

    /**
     * Extension request transformers of this run. The engine applies them first, before the
     * provider level and model level transformers. Empty when no extension declares any.
     */
    @Builder.Default
    @NonNull
    List<RequestTransformer> extensionRequestTransformers = List.of();


    /**
     * Returns the id to send in the request body: {@link #modelId} when set, else
     * {@link #modelName}.
     *
     * @return Effective model id for the wire.
     */
    public String effectiveModelId() {
        return modelId == null || modelId.isBlank() ? modelName : modelId;
    }
}
