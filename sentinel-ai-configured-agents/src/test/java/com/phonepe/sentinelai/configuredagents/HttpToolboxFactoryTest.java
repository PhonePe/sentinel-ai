/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.configuredagents;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;

import okhttp3.OkHttpClient;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link InMemoryAgentConfigurationSource}.
 */
@SuppressWarnings({
        "unchecked"
})
class HttpToolboxFactoryTest {

    private static HttpCallSpec createSpec() {
        return HttpCallSpec.builder()
                .method(HttpCallSpec.HttpMethod.GET)
                .path("/")
                .build();
    }

    private static void ensureResolution(HttpToolSource<?, ?> toolSource,
                                         HttpCallSpec callSpec,
                                         HttpToolboxFactory factory) {
        final var upstream = "test-upstream";
        when(toolSource.upstreams()).thenReturn(List.of(upstream));
        when(toolSource.resolve(upstream, "testFunc", "")).thenReturn(callSpec);
        assertTrue(factory.create(upstream).isPresent());
        assertFalse(factory.create("non-existent-upstream").isPresent());
    }

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

    @Test
    void testHttpToolboxFactoryWithProvidingClientFail() {
        final var objectMapper = JsonUtils.createMapper();
        final var toolSource = mock(HttpToolSource.class);
        final var callSpec = createSpec();
        final var factory = HttpToolboxFactory.httpClientProvidingBuilder()
                .okHttpClientProvider(upstream -> null)
                .objectMapper(objectMapper)
                .toolConfigSource(toolSource)
                .upstreamResolver(UpstreamResolver::direct)
                .build();
        final var upstream = "test-upstream";
        when(toolSource.upstreams()).thenReturn(List.of(upstream));
        when(toolSource.resolve(upstream, "testFunc", "")).thenReturn(callSpec);
        assertThrows(NullPointerException.class,
                     () -> factory.create(upstream).orElseThrow());
    }

    @Test
    void testNonNullGuards() {
        final var objectMapper = JsonUtils.createMapper();
        final var toolSource = mock(HttpToolSource.class);
        final var okHttpClient = mock(OkHttpClient.class);
        Function<String, UpstreamResolver> upstreamResolver = UpstreamResolver::direct;

        // okHttpClient null
        assertThrows(NullPointerException.class,
                     () -> HttpToolboxFactory.builder()
                             .okHttpClient(null)
                             .objectMapper(objectMapper)
                             .toolConfigSource(toolSource)
                             .upstreamResolver(upstreamResolver)
                             .build());

        // objectMapper null
        assertThrows(NullPointerException.class,
                     () -> HttpToolboxFactory.builder()
                             .okHttpClient(okHttpClient)
                             .objectMapper(null)
                             .toolConfigSource(toolSource)
                             .upstreamResolver(upstreamResolver)
                             .build());

        // toolConfigSource null
        assertThrows(NullPointerException.class,
                     () -> HttpToolboxFactory.builder()
                             .okHttpClient(okHttpClient)
                             .objectMapper(objectMapper)
                             .toolConfigSource(null)
                             .upstreamResolver(upstreamResolver)
                             .build());

        // upstreamResolver null
        assertThrows(NullPointerException.class,
                     () -> HttpToolboxFactory.builder()
                             .okHttpClient(okHttpClient)
                             .objectMapper(objectMapper)
                             .toolConfigSource(toolSource)
                             .upstreamResolver(null)
                             .build());

        assertThrows(NullPointerException.class,
                     () -> HttpToolboxFactory.httpClientProvidingBuilder()
                             .okHttpClientProvider(null)
                             .objectMapper(objectMapper)
                             .toolConfigSource(toolSource)
                             .upstreamResolver(upstreamResolver)
                             .build());
    }

}
