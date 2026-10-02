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

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import okhttp3.Request;

import java.util.Map;

/**
 * A {@link RequestTransformer} that adds fixed headers to every outgoing request.
 */
@Value
@Builder
@Jacksonized
public class ExtraHeadersRequestTransformer implements RequestTransformer {

    /**
     * Headers to apply; header name to header value.
     */
    @NonNull
    @Singular
    Map<String, String> headers;

    @Override
    public void transform(final Request.Builder requestBuilder,
                          final ObjectNode body,
                          final RequestTransformerContext ctx) {
        headers.forEach(requestBuilder::header);
    }
}
