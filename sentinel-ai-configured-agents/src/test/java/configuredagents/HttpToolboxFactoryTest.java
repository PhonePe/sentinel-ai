package configuredagents;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link InMemoryAgentConfigurationSource}.
 */
@SuppressWarnings({"unchecked"})
class HttpToolboxFactoryTest {

    @Test
    void testHttpToolboxFactory() {
        final var objectMapper = JsonUtils.createMapper();
        final var toolSource = mock(HttpToolSource.class);
        final var okHttpClient = mock(OkHttpClient.class);
        final var callSpec = createSpec();
        final var factory = HttpToolboxFactory.builder()
                .okHttpClient(okHttpClient)
                .objectMapper(objectMapper)
                .toolConfigSource(toolSource)
                .upstreamResolver(UpstreamResolver::direct)
                .build();
        ensureResolution(toolSource, callSpec, factory);
    }

    @Test
    void testHttpToolboxFactoryWithProvidingClient() {
        final var objectMapper = JsonUtils.createMapper();
        final var toolSource = mock(HttpToolSource.class);
        final var okHttpClient = mock(OkHttpClient.class);
        final var callSpec = createSpec();
        final var factory = HttpToolboxFactory.httpClientProvidingBuilder()
                .okHttpClientProvider(upstream -> okHttpClient)
                .objectMapper(objectMapper)
                .toolConfigSource(toolSource)
                .upstreamResolver(UpstreamResolver::direct)
                .build();
        ensureResolution(toolSource, callSpec, factory);
    }

    private static HttpCallSpec createSpec() {
        return HttpCallSpec.builder()
                .method(HttpCallSpec.HttpMethod.GET)
                .path("/")
                .build();
    }

    private static void ensureResolution(
            HttpToolSource<?, ?> toolSource,
            HttpCallSpec callSpec,
            HttpToolboxFactory factory) {
        final var upstream = "test-upstream";
        when(toolSource.upstreams()).thenReturn(List.of(upstream));
        when(toolSource.resolve(upstream, "testFunc", ""))
                .thenReturn(callSpec);
        assertTrue(factory.create(upstream).isPresent());
        assertFalse(factory.create("non-existent-upstream").isPresent());
    }

}