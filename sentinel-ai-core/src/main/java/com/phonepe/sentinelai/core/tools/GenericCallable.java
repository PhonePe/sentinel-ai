package com.phonepe.sentinelai.core.tools;

import lombok.Value;

import java.util.concurrent.Callable;

/**
 *
 */
@Value
public class GenericCallable implements CallableBase {
    Callable<?> callable;
}
