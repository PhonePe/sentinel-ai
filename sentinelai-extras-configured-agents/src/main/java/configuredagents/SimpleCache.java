package configuredagents;

import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Caching factory for creating instances of {@link ToolBox}.
 */
public class SimpleCache<T> {
    private final Map<String, T> toolboxCache = new ConcurrentHashMap<>();
    private final Function<String, T> toolboxCreator;

    public SimpleCache(
            @NonNull Function<String, T> toolboxCreator) {
        this.toolboxCreator = toolboxCreator;
    }

    public Optional<T> find(String upstream) {
        return Optional.of(toolboxCache.computeIfAbsent(
                upstream,
                toolboxCreator));
    }
}
