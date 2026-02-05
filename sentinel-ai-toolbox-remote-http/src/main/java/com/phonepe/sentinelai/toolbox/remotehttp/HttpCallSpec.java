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

package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A generic specification for a remote HTTP call
 */
@Value
@Builder
@With
public class HttpCallSpec {
    public enum HttpMethod {
        GET, PUT, POST, HEAD, DELETE
    }

    @NonNull
    HttpCallSpec.HttpMethod method;
    @NonNull
    String path;
    Map<String, List<String>> headers;
    String body;
    String contentType;

    UnaryOperator<String> responseTransformer;
}
