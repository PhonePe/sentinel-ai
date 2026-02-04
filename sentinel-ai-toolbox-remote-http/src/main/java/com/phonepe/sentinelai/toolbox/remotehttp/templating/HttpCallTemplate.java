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

package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Map;

/**
 * A generic specification for a remote HTTP call. Parts of this can be templatized
 */
@Value
@Builder
@With
public class HttpCallTemplate {
    /**
     * The type of templating engine used for the template.
     * <p>
     * This can be used to determine how to process the template content.
     */
    public enum TemplateType {
        /**
         * A simple text template, where the content is a plain string. No substitution is possible
         */
        TEXT,
        /**
         * A template that uses a string substitution engine, uses Apache Commons Lang's StrSubstitutor.
         * This allows for variable substitution within the template content. Templates can be written as ${variable}.
         * Variable values are received from LLM as parameters to the actual tool that is exposed to it.
         */
        TEXT_SUBSTITUTOR,

        HANDLEBARS
    }

    /**
     * A template for a remote HTTP call.
     * <p>
     * This is used to define the structure of the HTTP call, including the method, path, headers, body, and response mapper.
     * The path, headers, body, and response mapper can be templatized using different templating engines.
     */
    @Value
    @Builder
    public static class Template {
        @NonNull TemplateType type;
        @NonNull String content;

        /**
         * Creates a new text Template
         *
         * @param content the content of the template
         * @return a new Template instance
         */
        public static Template text(String content) {
            return Template.builder()
                    .type(TemplateType.TEXT)
                    .content(content)
                    .build();
        }

        /**
         * Creates a new string substitutor Template
         *
         * @param content the content of the template, which can contain variables in the form of ${variable}
         * @return a new Template instance
         */
        public static Template textSubstitutor(String content) {
            return Template.builder()
                    .type(TemplateType.TEXT_SUBSTITUTOR)
                    .content(content)
                    .build();
        }

        public static Template handlebars(String content) {
            return Template.builder()
                    .type(TemplateType.HANDLEBARS)
                    .content(content)
                    .build();
        }

    }
    /**
     * The HTTP method to use for the call.
     */
    @NonNull
    HttpCallSpec.HttpMethod method;
    /**
     * The path to call, can be a template. Example: /api/v1/location/${user}
     */
    @NonNull
    Template path;

    /**
     * Headers to send with the call, each value can be a template. Example: {"Authorization": "Bearer ${token}"}
     */
    Map<String, List<Template>> headers;

    /**
     * The body of the HTTP call, can be a template. Example: {"name": "${name}"}. Supported only in POST and PUT methods.
     */
    Template body;
    /**
     * The content type of the body, if applicable. Example: application/json
     */
    String contentType;
}
