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

    @NonNull
    String modelName;

    @NonNull
    String baseUrl;

    String userId;

    String runId;

    ModelSettings modelSettings;

    @NonNull
    Map<String, ExecutableTool> tools;

    @NonNull
    List<ModelOutputDefinition> outputDefinitions;

    /**
     * Compliant JSON schema of the output definitions; used for structured output fields.
     */
    ObjectNode outputSchema;

    @NonNull
    OutputGenerationMode outputGenerationMode;

    /**
     * Free-form extras node; merged into the request body last by {@link WireProtocol#applyExtras}.
     */
    JsonNode extras;

    /**
     * True when the call streams over SSE; protocols set the provider stream fields from it.
     */
    @Builder.Default
    boolean streaming = false;

    /**
     * Model id sent in the request body; {@link #modelName} when null.
     */
    String modelId;

    @Builder.Default
    ModelOptions.ToolChoice toolChoice = ModelOptions.DEFAULT_TOOL_CHOICE;

    @NonNull
    ObjectMapper mapper;

    @Builder.Default
    @NonNull
    List<RequestTransformer> extensionRequestTransformers = List.of();


    /**
     * @return The id to send in the request body: {@link #modelId} when set, else {@link #modelName}.
     */
    public String effectiveModelId() {
        return modelId == null || modelId.isBlank() ? modelName : modelId;
    }
}
