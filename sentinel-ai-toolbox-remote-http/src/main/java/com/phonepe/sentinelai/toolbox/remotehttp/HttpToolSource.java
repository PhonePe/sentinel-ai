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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Interface for a source of tools.
 */
public interface HttpToolSource<T extends HttpTool, S extends HttpToolSource<T, S>> {

    /**
     * List all known tools for the given upstream
     *
     * @param upstream The upstream service identifier
     * @return List of tool configurations
     */
    List<HttpToolMetadata> list(String upstream);

    /**
     * Register a tool for the given upstream.
     *
     * @param upstream Upstream service identifier
     * @param tool     Definition for the tool
     * @return this
     */
    S register(String upstream, List<T> tool);

    /**
     * Register a tool for the given upstream.
     *
     * @param upstream Upstream service identifier
     * @param tool     Definition for the tool
     * @return this
     */
    default S register(String upstream, T... tool) {
        Objects.requireNonNull(tool, "At least one tool is needed to be passed as argument");
        return register(upstream, Arrays.asList(tool));
    }

    /**
     * Resolve a tool for the given upstream and tool name.
     *
     * @param upstream  The upstream service identifier
     * @param toolName  The name of the tool being called
     * @param arguments The arguments for the tool as received from LLM
     * @return A fully resolved remote HTTP call specification
     * @throws IllegalArgumentException if the tool is not found for the given {upstream, toolName} combination
     */
    HttpCallSpec resolve(String upstream, String toolName, String arguments);

    /**
     * Get a list of known upstreams
     *
     * @return List of known upstreams
     */
    List<String> upstreams();
}
