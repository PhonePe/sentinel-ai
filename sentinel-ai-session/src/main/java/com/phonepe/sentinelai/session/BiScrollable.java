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

package com.phonepe.sentinelai.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * A container for a paginated list of items, providing pointers for scrolling in both directions.
 *
 * @param <T> The type of items in the response.
 */
@Value
@Builder
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class BiScrollable<T> {
    @Value
    public static class DataPointer {
        /**
         * A pointer to fetch the next page of older items. Will be null if there are no older items or if the query
         * direction was NEWER.
         */
        String older;

        /**
         * A pointer to fetch the next page of newer items. Will be null if there are no newer items or if the query
         * direction was OLDER.
         */
        String newer;
    }

    /**
     * The list of items retrieved in the current page.
     */
    List<T> items;

    /**
     * Pointers to data items.
     */
    DataPointer pointer;
}
