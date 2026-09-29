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

package com.phonepe.sentinelai.models.provider;

import com.phonepe.sentinelai.models.wire.WireProtocol;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Everything about the remote model endpoint: base URL, endpoint prefix, authentication and
 * the wire protocol. Models pair one provider with a model name; the provider owns where and
 * how the calls travel.
 */
@Getter
@Builder
public class Provider {

    /**
     * Root URL of the provider endpoint including any path prefix, for example
     * {@code https://api.openai.com/v1}. The protocol appends only its path after it.
     */
    @NonNull
    private final String baseUrl;

    /**
     * Wire protocol of the provider: request body shape, response decoding and endpoint
     * path.
     */
    @NonNull
    private final WireProtocol protocol;

    /**
     * Authentication applied to every request; null means no auth is applied by the model.
     * Transport level auth (OkHttp interceptors) stays available.
     */
    private final Auth auth;

}
