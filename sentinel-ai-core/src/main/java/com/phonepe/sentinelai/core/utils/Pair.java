package com.phonepe.sentinelai.core.utils;

import lombok.Value;

/**
 * A pair of two values. Comes in handy when you need to return two values from a lambda.
 * (Why doesn't Java have this in the standard library?)
 */
@Value
public class Pair <T,U>{
    T first;
    U second;

    public static <T,U> Pair<T,U> of(T first, U second) {
        return new Pair<>(first, second);
    }
}
