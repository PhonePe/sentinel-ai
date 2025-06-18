package configuredagents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpToolSource;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import okhttp3.OkHttpClient;

import java.util.Optional;
import java.util.function.Function;

/**
 *
 */
@AllArgsConstructor
public class HttpToolboxFactory<S extends TemplatizedHttpToolSource<S>> {
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final S toolSource;
    private final Function<String, UpstreamResolver> endpointProviderFactory;

    public Optional<HttpToolBox> create(@NonNull final String upstream) {
        if (!toolSource.upstreams().contains(upstream)) {
            return Optional.empty();
        }
        return Optional.of(new HttpToolBox(
                upstream,
                okHttpClient,
                toolSource,
                objectMapper,
                endpointProviderFactory.apply(upstream)));
    }
}
