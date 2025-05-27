package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing the parameter types supported by the RemoteHttpTool templates.
 */
@AllArgsConstructor
@Getter
public enum HttpToolParameterType {
    STRING(String.class),
    BOOLEAN(Boolean.class),
    INTEGER(Integer.class),
    LONG(Long.class),
    FLOAT(Float.class),
    DOUBLE(Double.class),
    BYTE(Byte.class),
    SHORT(Short.class),
    CHARACTER(Character.class),
    STRING_ARRAY(String[].class),
    BOOLEAN_ARRAY(Boolean[].class),
    INTEGER_ARRAY(Integer[].class),
    LONG_ARRAY(Long[].class),
    FLOAT_ARRAY(Float[].class),
    DOUBLE_ARRAY(Double[].class),
    BYTE_ARRAY(Byte[].class),
    SHORT_ARRAY(Short[].class),
    CHARACTER_ARRAY(Character[].class)
    ;

    /**
     * The java raw type specific to a parameter type.
     */
    private final Class<?> rawType;
}
