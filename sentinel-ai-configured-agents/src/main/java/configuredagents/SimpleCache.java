package configuredagents;

import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A simple cache that allows for lazy loading of objects based on a key.
 */
public class SimpleCache<T> {
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final Function<String, T> creator;

    public SimpleCache(@NonNull Function<String, T> creator) {
        this.creator = creator;
    }

    public Optional<T> find(@NonNull String key) {
        return Optional.of(cache.computeIfAbsent(key, creator));
    }
}
