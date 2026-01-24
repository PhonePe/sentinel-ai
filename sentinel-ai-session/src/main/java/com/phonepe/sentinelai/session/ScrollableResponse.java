package com.phonepe.sentinelai.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * A scrollable list of items
 */
@Value
@Builder
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ScrollableResponse<T> {
    List<T> items;
    String older;
    String newer;
}
