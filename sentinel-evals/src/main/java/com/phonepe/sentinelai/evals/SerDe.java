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

package com.phonepe.sentinelai.evals;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class SerDe {
    private static final AtomicReference<ObjectMapper> MAPPER = new AtomicReference<>();

    private SerDe() {
    }

    public static void initialize(ObjectMapper objectMapper) {
        MAPPER.set(Objects.requireNonNull(objectMapper, "objectMapper cannot be null"));
    }

    public static ObjectMapper mapper() {
        return MAPPER.updateAndGet(existing -> existing != null ? existing : JsonUtils.createMapper());
    }
}
