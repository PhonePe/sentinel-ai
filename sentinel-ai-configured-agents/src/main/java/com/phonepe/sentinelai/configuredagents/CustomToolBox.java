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

package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A toolbox of custom tools that can be registered to the {@link ConfiguredAgentFactory}
 */
public class CustomToolBox implements ToolBox {

    private final String name;
    private final Map<String, ExecutableTool> tools = new ConcurrentHashMap<>();

    @Builder
    public CustomToolBox(@NonNull String name, @Singular Collection<ExecutableTool> tools) {
        this.name = name;
        this.registerTools(tools);
    }

    private CustomToolBox(final String name, Map<String, ExecutableTool> tools) {
        this.name = name;
        this.tools.putAll(tools);
    }

    public static CustomToolBox filter(@NonNull final String agentName, @NonNull final String sourceName,
            @NonNull final Map<String, ExecutableTool> tools, @NonNull final Set<String> exposedTools) {
        final var sourceTools = Objects.requireNonNullElseGet(tools, Map::<String, ExecutableTool>of);
        final var toolBoxName = "%s-%s".formatted(agentName, sourceName);
        return new CustomToolBox(toolBoxName, sourceTools.entrySet()
                .stream()
                .filter(entry -> exposedTools.isEmpty() || exposedTools.contains(entry.getValue()
                        .getToolDefinition()
                        .getName()))
                .collect(Collectors.toMap(entry -> entry.getValue().getToolDefinition().getId(), Map.Entry::getValue)));
    }

    public static CustomToolBox filter(@NonNull final String agentName, @NonNull final ToolBox toolBox,
            @NonNull final Set<String> exposedTools) {
        return CustomToolBox.filter(agentName, toolBox.name(), toolBox.tools(), exposedTools);
    }

    @Override
    public String name() {
        return name;
    }

    public CustomToolBox registerToolBox(ToolBox toolBox) {
        return this.registerTools(toolBox.tools());
    }

    public CustomToolBox registerTools(Collection<ExecutableTool> tools) {
        return this.registerTools(Objects.<Collection<ExecutableTool>>requireNonNullElseGet(tools, List::of)
                .stream()
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().getName(), Function.identity())));
    }

    public CustomToolBox registerTools(ExecutableTool... tools) {
        return registerTools(Arrays.asList(tools));
    }

    public CustomToolBox registerTools(@NonNull Map<String, ExecutableTool> tools) {
        this.tools.putAll(tools);
        return this;
    }

    public CustomToolBox registerToolsFromObject(Object object) {
        return this.registerTools(ToolUtils.readTools(object));
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return Map.copyOf(tools);
    }
}
