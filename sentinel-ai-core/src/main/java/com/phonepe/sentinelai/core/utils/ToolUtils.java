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

package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.tools.ToolMethodInfo;
import com.phonepe.sentinelai.core.tools.ToolParameter;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Reads tools from a class and creates a map of tool names to the tools
 */
@Slf4j
@UtilityClass
public class ToolUtils {
    @SneakyThrows
    public static List<Object> convertToRealParams(ToolMethodInfo methodInfo,
                                                   String params,
                                                   ObjectMapper objectMapper) {
        final var paramNodes = objectMapper.readTree(params);
        return methodInfo.parameters().stream().map(param -> {
            final var paramName = param.getName();
            final var paramType = param.getType();
            final var paramNode = paramNodes.get(paramName);
            return objectMapper.convertValue(paramNode, paramType);
        }).toList();
    }

    public static Map<String, ExecutableTool> readTools(Object instance) {
        Class<?> type = instance.getClass();
        final var tools = new HashMap<String, ExecutableTool>();
        while (type != Object.class) { // Traverse up till we reach Object
            final var className = type.getSimpleName();
            tools.putAll(Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Tool.class))
                    .map(method -> {
                        final var callableTool = createCallableToolFromLocalMethod(instance,
                                                                                   method);
                        log.info("Created tool: {} from {}::{}",
                                 callableTool.getToolDefinition().getId(),
                                 className,
                                 method.getName());
                        return callableTool;
                    })
                    .collect(toMap(tool -> tool.getToolDefinition().getId(),
                                   Function.identity())));
            type = type.getSuperclass();
        }
        return tools;
    }


    public static Pair<ToolDefinition, ToolMethodInfo> toolMetadata(String prefix,
                                                                    Method method) {
        final var toolDef = method.getAnnotation(Tool.class);
        final var params = new ArrayList<ToolParameter>();
        final var paramTypes = method.getParameterTypes();
        final var declParams = method.getParameters();
        boolean hasContext = false;
        for (var i = 0; i < declParams.length; i++) {
            final var paramType = paramTypes[i];
            final var param = declParams[i];
            if (paramType.equals(AgentRunContext.class)) {
                hasContext = true;
                continue;
            }

            final var paramAnnotation = param.getAnnotation(
                                                            JsonPropertyDescription.class);
            final var description = (null != paramAnnotation) ? paramAnnotation
                    .value() : "";
            params.add(new ToolParameter(param.getName(),
                                         description,
                                         TypeFactory.defaultInstance()
                                                 .constructType(paramType)));
        }
        final var toolName = toolDef.name().isBlank() ? method.getName()
                : toolDef.name();
        return Pair.of(ToolDefinition.builder()
                .id(AgentUtils.id(prefix, toolName))
                .name(toolName)
                .description(toolDef.value())
                .contextAware(hasContext)
                .strictSchema(true)
                .terminal(false)
                .build(),
                       new ToolMethodInfo(params,
                                          method,
                                          method.getReturnType()));
    }

    private static InternalTool createCallableToolFromLocalMethod(Object instance,
                                                                  Method method) {
        final var toolMetadata = toolMetadata(instance.getClass()
                .getSimpleName(), method);
        return new InternalTool(toolMetadata.getFirst(),
                                toolMetadata.getSecond(),
                                instance);
    }
}
