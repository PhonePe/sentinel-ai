package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.CaseFormat;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.tools.ToolParameter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Reads tools from a class and creates a map of tool names to the tools
 */
@Slf4j
@UtilityClass
public class ToolReader {
    public static Map<String, CallableTool> readTools(Object instance) {
        Class<?> type = instance.getClass();
        final var tools = new HashMap<String, CallableTool>();
        while (type != Object.class) { // Traverse up till we reach Object
            final var className = type.getSimpleName();
            tools.putAll(Arrays.stream(type.getDeclaredMethods())
                                 .filter(method -> method.isAnnotationPresent(Tool.class))
                                 .map(method -> {
                                     final var callableTool = createCallableToolFromLocalMethods(
                                             instance,
                                             method);
                                     log.info("Created tool: {} from {}::{}",
                                              callableTool.getToolDefinition().getName(),
                                              className,
                                              method.getName());
                                     return callableTool;
                                 })
                                 .collect(toMap(tool -> tool.getToolDefinition().getName(), Function.identity())));
            type = type.getSuperclass();
        }
        return tools;
    }

    private static CallableTool createCallableToolFromLocalMethods(Object instance, Method method) {
        final var toolDef = method.getAnnotation(Tool.class);
        final var params = new HashMap<String, ToolParameter>();
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

            final var paramAnnotation = param.getAnnotation(JsonPropertyDescription.class);
            final var description = (null != paramAnnotation) ? paramAnnotation.value() : "";
            params.put(param.getName(),
                       new ToolParameter(param.getName(),
                                         description,
                                         TypeFactory.defaultInstance().constructType(paramType)));
        }
        final var toolClassName = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE)
                .convert(instance.getClass().getSimpleName());
        final var toolName = toolClassName + "_" + CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE)
                .convert((toolDef.name().isBlank() ? method.getName() : toolDef.name()));
        return new CallableTool(
                ToolDefinition.builder()
                        .name(toolName)
                        .description(toolDef.value())
                        .contextAware(hasContext)
                        .parameters(params)
                        .build(),
                method,
                instance,
                method.getReturnType());
    }
}
