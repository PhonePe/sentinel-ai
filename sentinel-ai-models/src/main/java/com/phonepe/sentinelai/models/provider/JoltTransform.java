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

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * One Jolt operation of a {@link JoltRequestTransformer}. The {@code operation} field is a valid
 * Jolt operation name (for example {@code default}, {@code add}, {@code remove}, {@code shift},
 * {@code modify-overwrite-beta}) and the {@code spec} field is the spec map of that operation.
 *
 * <p>For example, the pair below sets {@code chat_template_kwargs.thinking} to {@code false} when
 * the field is absent:
 * <pre>
 * operation: "default"
 * spec:
 * chat_template_kwargs:
 * thinking: false
 * </pre>
 */
@Value
@Builder
@Jacksonized
public class JoltTransform {

    @NonNull
    String operation;

    @NonNull
    Map<String, Object> spec;
}
