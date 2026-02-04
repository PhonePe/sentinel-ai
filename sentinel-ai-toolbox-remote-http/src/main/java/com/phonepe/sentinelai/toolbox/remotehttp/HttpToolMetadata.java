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

package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.*;

import java.util.Map;

/**
 * Metadata for a HTTP Tool.
 */
@Value
@Builder
@With
public class HttpToolMetadata {

    /**
     * Metadata for all the tool parameters
     */
    @Value
    @Builder
    @AllArgsConstructor
    public static class HttpToolParameterMeta {

        /**
         * A description for the parameter. Providing a good description will help the LLM send the correct values to it.
         */
        @NonNull
        String description;

        /**
         * Type of the parameter. Only simple types are supported for performance and simplicity.
         */
        @NonNull
        HttpToolParameterType type;
    }

    /**
     * The name of the tool being registered
     */
    @NonNull
    String name;

    /**
     * A detailed description of the tool. This will be passed to LLM and will be used by the LLM to select and use it
     */
    @NonNull
    String description;

    /**
     * List of parameters that the LLM needs to pass to the tool
     */
    Map<String, HttpToolParameterMeta> parameters;
}
