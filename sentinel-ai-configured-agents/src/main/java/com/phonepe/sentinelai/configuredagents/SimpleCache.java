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

import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A simple cache that allows for lazy loading of objects based on a key.
 */
public class SimpleCache<T> {
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final Function<String, T> creator;

    public SimpleCache(@NonNull Function<String, T> creator) {
        this.creator = creator;
    }

    public Optional<T> find(@NonNull String key) {
        return Optional.of(cache.computeIfAbsent(key, creator));
    }
}
