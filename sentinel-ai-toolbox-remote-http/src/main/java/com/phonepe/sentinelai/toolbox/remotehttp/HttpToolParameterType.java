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

package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing the parameter types supported by the RemoteHttpTool templates.
 */
@AllArgsConstructor
@Getter
public enum HttpToolParameterType {
    STRING(String.class), BOOLEAN(Boolean.class), INTEGER(Integer.class), LONG(
                                                                               Long.class),
    FLOAT(Float.class), DOUBLE(Double.class), BYTE(Byte.class), SHORT(
                                                                      Short.class),
    CHARACTER(Character.class), STRING_ARRAY(String[].class), BOOLEAN_ARRAY(
                                                                            Boolean[].class),
    INTEGER_ARRAY(Integer[].class), LONG_ARRAY(Long[].class), FLOAT_ARRAY(
                                                                          Float[].class),
    DOUBLE_ARRAY(Double[].class), BYTE_ARRAY(Byte[].class), SHORT_ARRAY(
                                                                        Short[].class),
    CHARACTER_ARRAY(Character[].class);

    /**
     * The java raw type specific to a parameter type.
     */
    private final Class<?> rawType;
}
