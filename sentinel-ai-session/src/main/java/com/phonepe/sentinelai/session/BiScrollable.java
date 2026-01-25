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
         * A pointer to fetch the next page of older items. Will be null if there are no older items or if the query direction was NEWER.
         */
        String older;

        /**
         * A pointer to fetch the next page of newer items. Will be null if there are no newer items or if the query direction was OLDER.
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
