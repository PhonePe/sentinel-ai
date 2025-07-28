package configuredagents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpToolSource;
import lombok.Builder;
import lombok.NonNull;
import okhttp3.OkHttpClient;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Factory for creating instances of {@link HttpToolBox} using a provided
 * {@link TemplatizedHttpToolSource}, {@link OkHttpClient}, {@link ObjectMapper},
 * and an endpoint provider factory. We do not apply tool filters here, that is done in the
 * {@link ConfiguredAgentFactory} where we can apply filters based on the agent configuration.
 */
public class HttpToolboxFactory {
    private final Function<String, OkHttpClient> okHttpClientProvider;

    @NonNull
    private final ObjectMapper objectMapper;

    @NonNull
    private final HttpToolSource<TemplatizedHttpTool, ?> toolConfigSource;

    @NonNull
    private final Function<String, UpstreamResolver> upstreamResolver;

    @Builder(builderClassName = "DefaultHttpToolboxFactoryBuilder")
    public HttpToolboxFactory(
            @NonNull OkHttpClient okHttpClient,
            @NonNull ObjectMapper objectMapper,
            @NonNull HttpToolSource<TemplatizedHttpTool, ?> toolConfigSource,
            @NonNull Function<String, UpstreamResolver> upstreamResolver) {
        this(name -> okHttpClient,
             objectMapper,
             toolConfigSource,
             upstreamResolver);
    }

    @Builder(builderClassName = "ProvidingHttpToolboxFactoryBuilder", builderMethodName = "httpClientProvidingBuilder")
    public HttpToolboxFactory(
            @NonNull Function<String, OkHttpClient> okHttpClientProvider,
            @NonNull ObjectMapper objectMapper,
            @NonNull HttpToolSource<TemplatizedHttpTool, ?> toolConfigSource,
            @NonNull Function<String, UpstreamResolver> upstreamResolver) {
        this.okHttpClientProvider = okHttpClientProvider;
        this.objectMapper = objectMapper;
        this.toolConfigSource = toolConfigSource;
        this.upstreamResolver = upstreamResolver;
    }

    public Optional<HttpToolBox> create(@NonNull final String upstream) {
        if (!toolConfigSource.upstreams().contains(upstream)) {
            return Optional.empty();
        }
        return Optional.of(new HttpToolBox(
                upstream,
                Objects.requireNonNull(okHttpClientProvider.apply(upstream),
                                       "Could not resolve http client for upstream: " + upstream),
                toolConfigSource,
                objectMapper,
                upstreamResolver.apply(upstream)));
    }
}
