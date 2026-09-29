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

package com.phonepe.sentinelai.models;

import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.Auth;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.wire.TestWireProtocol;
import com.phonepe.sentinelai.models.wire.WireProtocol;

import lombok.experimental.UtilityClass;
import okhttp3.OkHttpClient;

/**
 * Test helpers for {@link ConfiguredModel}. Builds models around the test wire protocol; when
 * real endpoints are enabled (system property {@code sentinelai.useRealEndpoints}), the
 * production Chat Completions protocol is used so the same tests run against a real endpoint.
 */
@UtilityClass
public class TestModels {

    /**
     * Builds the test auth: no auth in mock mode, a Bearer header in real mode.
     *
     * @return Auth for the test provider, or null when no auth applies.
     */
    public static Auth testAuth() {
        final var apiKey = TestStubs.getTestProperty("AZURE_API_KEY", null);
        return apiKey == null ? null : HeaderAuth.bearer(apiKey);
    }

    /**
     * Builds a test model.
     *
     * @param modelName  Model name.
     * @param baseUrl    Base URL of the endpoint.
     * @param httpClient OkHttp client used for the calls; may be null.
     * @return ConfiguredModel around the test protocol.
     */
    public static ConfiguredModel testModel(final String modelName,
                                            final String baseUrl,
                                            final OkHttpClient httpClient) {
        return testModel(modelName, baseUrl, httpClient, ModelOptions.DEFAULT);
    }

    /**
     * Builds a test model.
     *
     * @param modelName    Model name.
     * @param baseUrl      Base URL of the endpoint.
     * @param httpClient   OkHttp client used for the calls; may be null.
     * @param modelOptions Model options; may be null.
     * @return ConfiguredModel around the test protocol.
     */
    public static ConfiguredModel testModel(final String modelName,
                                            final String baseUrl,
                                            final OkHttpClient httpClient,
                                            final ModelOptions modelOptions) {
        return ConfiguredModel.builder()
                .modelName(modelName)
                .provider(testProvider(baseUrl))
                .httpClient(httpClient)
                .modelOptions(modelOptions)
                .build();
    }

    /**
     * Builds the test wire protocol: the test protocol in mock mode, the production
     * Chat Completions protocol in real mode.
     *
     * @return Wire protocol for the test model.
     */
    public static WireProtocol testProtocol() {
        return TestStubs.useRealEndpoints() ? new ChatCompletionsProtocol() : new TestWireProtocol();
    }

    /**
     * Builds the test provider for the given base URL.
     *
     * @param baseUrl Base URL of the endpoint; the test wire protocol appends
     *                {@code /chat/completions}.
     * @return Provider around the test protocol.
     */
    public static Provider testProvider(final String baseUrl) {
        return Provider.builder()
                .baseUrl(baseUrl)
                .protocol(testProtocol())
                .auth(testAuth())
                .build();
    }
}
