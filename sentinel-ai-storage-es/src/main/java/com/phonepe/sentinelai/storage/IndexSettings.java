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

package com.phonepe.sentinelai.storage;

import lombok.Builder;
import lombok.Value;

/**
 * Settings for index
 */
@Value
@Builder
public class IndexSettings {
    public static final int DEFAULT_SHARDS = 1;
    public static final int DEFAULT_REPLICAS = 0;
    public static final IndexSettings DEFAULT = new IndexSettings(DEFAULT_SHARDS, DEFAULT_REPLICAS);

    @Builder.Default
    int shards = DEFAULT_SHARDS;

    @Builder.Default
    int replicas = DEFAULT_REPLICAS;
}
