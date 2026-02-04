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

package com.phonepe.sentinelai.core.tools;

import com.phonepe.sentinelai.core.agent.Agent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A toolbox that can be used to combine multiple toolboxes into one abd expose only selected tools to the Agent.
 */
@Slf4j
public class ComposingToolBox implements ToolBox {
    private final Collection<? extends ToolBox> upstreams;
    private final Set<String> allowedMethods;
    private final String name;

    public ComposingToolBox(
            @NonNull Collection<? extends ToolBox> upstreams,
            @NonNull Set<String> allowedMethods) {
        this(upstreams, allowedMethods, null);
    }

    public ComposingToolBox(
            @NonNull Collection<? extends ToolBox> upstreams,
            @NonNull Set<String> allowedMethods,
            String name) {
        this.upstreams = upstreams;
        this.allowedMethods = allowedMethods;
        this.name = Objects.requireNonNullElseGet(
                name,
                () -> "composing-toolbox-%s".formatted(UUID.randomUUID()
                                                               .toString()));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        final var discoveredTools = upstreams.stream()
                .flatMap(toolBox -> toolBox.tools().entrySet().stream())
                .filter(entry -> allowedMethods.isEmpty() || allowedMethods.contains(entry.getValue()
                                                                                             .getToolDefinition()
                                                                                             .getName()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        log.debug("Tools exposed from composing tool box {}: {}", name, discoveredTools.keySet());
        return discoveredTools;
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void onToolBoxRegistrationCompleted(A agent) {
        upstreams.forEach(toolBox -> toolBox.onToolBoxRegistrationCompleted(agent));
    }
}
