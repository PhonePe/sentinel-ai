package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.List;

/**
 *
 */
@Value
public class SystemPromptSchema {
    @Value
    public static class ToolSummary {
        String name;
        String description;
    }

    @Value
    public static class PrimaryTask {
        String prompt;
        List<ToolSummary> tools;
    }

    @Value
    public static class SecondaryTask {
        String prompt;
    }

    PrimaryTask primaryTask;
    List<SecondaryTask> secondaryTasks; //Come from extensions

    @SneakyThrows
    public static String convert(SystemPromptSchema prompt, ObjectMapper xmlMapper) {
        return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt);
    }
}
