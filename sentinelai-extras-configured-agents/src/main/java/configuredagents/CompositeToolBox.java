package configuredagents;

import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
@AllArgsConstructor
public class CompositeToolBox implements ToolBox {
    private final ToolBox upstream;
    private final Set<String> allowedMethods;

    @Override
    public Map<String, ExecutableTool> tools() {
        return upstream.tools()
                .entrySet()
                .stream()
                .filter(entry -> allowedMethods.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
