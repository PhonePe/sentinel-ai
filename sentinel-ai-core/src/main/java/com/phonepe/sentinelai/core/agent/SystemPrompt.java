package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.Value;

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
    public static class PrimaryTask {
        private Object role;
        @JacksonXmlElementWrapper(localName = "tools")
        private List<ToolSummary> tool;
    }

    @Data
    public static class SecondaryTask {
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


    private String coreInstructions;
    private PrimaryTask primaryTask;
    @JacksonXmlElementWrapper(localName = "secondaryTasks")
    private List<SecondaryTask> secondaryTask; //Come from extensions
    private AdditionalData additionalData;
    @JacksonXmlElementWrapper(localName = "knowledge")
    private List<FactList> facts;
    @JacksonXmlElementWrapper(localName = "hints")
    private List<Object> hint;

    @SneakyThrows
    public static String convert(SystemPrompt prompt, ObjectMapper xmlMapper) {
        return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt);
    }
}
