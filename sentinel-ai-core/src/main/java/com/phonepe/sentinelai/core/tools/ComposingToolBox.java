package com.phonepe.sentinelai.core.tools;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A toolbox that can be used to combine multiple toolboxes into one abd expose only selected tools to the Agent.
 */
@AllArgsConstructor
public class ComposingToolBox implements ToolBox {
    @NonNull
    private final Collection<? extends ToolBox> upstreams;
    @NonNull
    private final Set<String> allowedMethods;

    @Override
    public Map<String, ExecutableTool> tools() {
        return upstreams.stream()
                .flatMap(toolBox -> toolBox.tools().entrySet().stream())
                .filter(entry -> allowedMethods.isEmpty() || allowedMethods.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
