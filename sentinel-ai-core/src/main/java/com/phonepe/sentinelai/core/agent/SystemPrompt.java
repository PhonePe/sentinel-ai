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

package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;

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
    @Data
    public static class AdditionalData {
        private String sessionId;
        private String userId;
        private Map<String, Object> customParams;
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

    @Value
    @Builder
    public static class ToolSummary {
        String name;
        String description;
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
