package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Schema for system prompts.
 */
@Data
public class SystemPrompt {
    @Value
    @Builder
    public static class ToolSummary {
        String name;
        String description;
    }

    @Data
    @Builder
    public static class Task {
        @NonNull
        private String objective;
        private String outputField;
        private Object instructions;
        private Object additionalInstructions;
        @JacksonXmlElementWrapper(localName = "tools")
        private List<ToolSummary> tool;
        @JacksonXmlElementWrapper(localName = "knowledge")
        private List<FactList> facts;
    }

    @Data
    public static class AdditionalData {
        private String sessionId;
        private String userId;
        private Map<String, Object> customParams;
    }

    private String name;
    private String coreInstructions;
    private Task primaryTask;
    @JacksonXmlElementWrapper(localName = "secondaryTasks")
    private List<Task> secondaryTask; //Come from extensions
    private AdditionalData additionalData;
    @JacksonXmlElementWrapper(localName = "knowledge")
    private List<FactList> facts;
    @JacksonXmlElementWrapper(localName = "hints")
    private List<Object> hint;
    private String currentTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);

    @SneakyThrows
    public static String convert(SystemPrompt prompt, ObjectMapper xmlMapper) {
        return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt);
    }
}
