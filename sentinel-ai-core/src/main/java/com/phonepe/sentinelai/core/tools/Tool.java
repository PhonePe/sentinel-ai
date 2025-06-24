package com.phonepe.sentinelai.core.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool that will be called by the LLM
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    /**
     * Name of the tool. If not provided, the method name will be used
     */
    String name() default "";

    /**
     * Description of the tool. This is mandatory
     */
    String value();

    /**
     * Number of retries for the tool in case of failures
     */
    int retries() default 0;
}
