package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;

/**
 * Schema for system prompts.
 */
@Data
public class SystemPromptSchema {
    @Data
    public static class ToolSummary {
        private String name;
        private String description;
    }

    @Data
    public static class PrimaryTask {
        private Object role;
        private List<ToolSummary> tools;
    }

    @Data
    public static class SecondaryTask {
        private String objective;
        private String outputField;
        private Object instructions;
        private Object additionalInstructions;
        private List<ToolSummary> tools;
    }

    @Data
    public static class AdditionalData {
        private String sessionId;
        private String userId;
    }


    private String coreInstructions;
    private PrimaryTask primaryTask;
    private List<SecondaryTask> secondaryTasks; //Come from extensions
    private AdditionalData additionalData;
    private List<Object> hints;

    @SneakyThrows
    public static String convert(SystemPromptSchema prompt, ObjectMapper xmlMapper) {
        return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt);
    }
}
