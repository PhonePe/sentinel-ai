package configuredagents;

import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.*;
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
    public CustomToolBox(
            @NonNull String name,
            @Singular Collection<ExecutableTool> tools) {
        this.name = name;
        this.registerTools(tools);
    }

    private CustomToolBox(final String name, Map<String, ExecutableTool> tools) {
        this.name = name;
        this.tools.putAll(tools);
    }

    public static CustomToolBox filter(
            @NonNull final String agentName,
            @NonNull final CustomToolBox toolBox,
            @NonNull final Set<String> exposedTools) {
        final var sourceTools = Objects.requireNonNullElseGet(toolBox.tools(), Map::<String, ExecutableTool>of);
        final var toolBoxName = "%s-%s".formatted(agentName, toolBox.name());
        return new CustomToolBox(
                toolBoxName,
                sourceTools.entrySet()
                        .stream()
                        .filter(entry -> exposedTools.isEmpty()
                                || exposedTools.contains(entry.getValue().getToolDefinition().getName()))
                        .collect(Collectors.toMap(
                                entry ->
                                        entry.getValue()
                                                .getToolDefinition()
                                                .getId(),
                                Map.Entry::getValue)));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return Map.copyOf(tools);
    }

    public CustomToolBox registerTools(@NonNull Map<String, ExecutableTool> tools) {
        this.tools.putAll(tools);
        return this;
    }

    public CustomToolBox registerTools(Collection<ExecutableTool> tools) {
        return this.registerTools(Objects.<Collection<ExecutableTool>>requireNonNullElseGet(tools, List::of)
                                  .stream()
                                  .collect(Collectors.toMap(tool -> tool.getToolDefinition().getName(),
                                                            Function.identity())));
    }

    public CustomToolBox registerTools(ExecutableTool... tools) {
        return registerTools(Arrays.asList(tools));
    }

    public CustomToolBox registerToolBox(ToolBox toolBox) {
        return this.registerTools(toolBox.tools());
    }

    public CustomToolBox registerToolsFromObject(Object object) {
        return this.registerTools(ToolUtils.readTools(object));
    }
}
