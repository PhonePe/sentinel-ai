package configuredagents;

import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
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
