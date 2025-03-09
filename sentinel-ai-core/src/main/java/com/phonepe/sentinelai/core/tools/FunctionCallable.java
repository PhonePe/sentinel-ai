package com.phonepe.sentinelai.core.tools;

import lombok.Value;

import java.lang.reflect.Method;

/**
 *
 */
@Value
public class FunctionCallable implements CallableBase {
    Method callable;
    Object instance;
    Class<?> returnType;


}
