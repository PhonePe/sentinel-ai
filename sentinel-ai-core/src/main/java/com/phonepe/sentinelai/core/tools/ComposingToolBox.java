package com.phonepe.sentinelai.core.tools;

import lombok.NonNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A toolbox that can be used to combine multiple toolboxes into one abd expose only selected tools to the Agent.
 */
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
        return upstreams.stream()
                .flatMap(toolBox -> toolBox.tools().entrySet().stream())
                .filter(entry -> allowedMethods.isEmpty() || allowedMethods.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
