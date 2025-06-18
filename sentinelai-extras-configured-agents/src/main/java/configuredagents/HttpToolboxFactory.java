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
  * Factory for creating instances of {@link HttpToolBox} using a provided
  * {@link TemplatizedHttpToolSource}, {@link OkHttpClient}, {@link ObjectMapper},
  * and an endpoint provider factory.
  *
  * @param <S> the type of the tool source extending {@link TemplatizedHttpToolSource}
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
