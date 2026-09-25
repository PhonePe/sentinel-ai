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

package com.phonepe.sentinelai.models.openai;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.models.AbstractModel;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.TokenCounter;
import com.phonepe.sentinelai.models.transport.ModelTransport;
import com.phonepe.sentinelai.models.transport.OkHttpModelTransport;
import com.phonepe.sentinelai.models.wire.WireProtocol;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import okhttp3.OkHttpClient;

import java.util.Objects;

/**
 * Chat Completions model over {@link AbstractModel}. Binds a {@link ChatCompletionsProtocol} and a
 * {@link ModelTransport} (OkHttp by default). Authentication: either set {@code apiKey} (sent as a
 * Bearer token) or leave it null and let the transport's {@link OkHttpClient} and its interceptors
 * handle auth.
 */
@Getter
public class ChatCompletionsModel extends AbstractModel {

    private final String baseUrl;

    @Builder
    protected ChatCompletionsModel(final String modelName,
                                   @NonNull final String baseUrl,
                                   final String apiKey,
                                   final ObjectMapper mapper,
                                   final ModelTransport transport,
                                   final WireProtocol protocol,
                                   final ModelOptions modelOptions,
                                   final TokenCounter tokenCounter) {
        super(modelName,
              Objects.requireNonNullElseGet(protocol,
                                            () -> new ChatCompletionsProtocol(
                                                                              Objects.requireNonNullElseGet(mapper,
                                                                                                            ObjectMapper::new),
                                                                              modelOptions,
                                                                              apiKey)),
              Objects.requireNonNullElseGet(transport, () -> OkHttpModelTransport.of(new OkHttpClient())),
              mapper,
              modelOptions,
              tokenCounter);
        this.baseUrl = baseUrl;
    }

    @Override
    protected String baseUrl() {
        return baseUrl;
    }
}
