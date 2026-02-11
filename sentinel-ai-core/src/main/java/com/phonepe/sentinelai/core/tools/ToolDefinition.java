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

package com.phonepe.sentinelai.core.tools;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

/**
 * Metadata for a tool that will be called by the LLM
 */
@Value
@With
@Builder
public class ToolDefinition {
    public static final int NO_RETRY = 0;
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int NO_TIMEOUT = -1;

    @NonNull
    String id;

    @NonNull
    String name;

    @NonNull
    String description;

    boolean contextAware;

    boolean strictSchema;

    boolean terminal;

    @Builder.Default
    int retries = NO_RETRY;

    @Builder.Default
    int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
}
