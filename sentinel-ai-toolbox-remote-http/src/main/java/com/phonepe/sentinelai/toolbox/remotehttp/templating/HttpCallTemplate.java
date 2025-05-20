package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.phonepe.sentinelai.toolbox.remotehttp.HttpRemoteCallSpec;
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
    public enum TemplateType {
        TEXT,
        JAVA_STR_FORMAT,
        STR_SUBSTITUTOR,
        HANDLEBARS
    }

    @Value
    @Builder
    public static class Template {
        @NonNull TemplateType type;
        @NonNull String content;

        public static Template text(String content) {
            return Template.builder()
                    .type(TemplateType.TEXT)
                    .content(content)
                    .build();
        }

        public static Template javaStrFormat(String content) {
            return Template.builder()
                    .type(TemplateType.JAVA_STR_FORMAT)
                    .content(content)
                    .build();
        }

        public static Template strSubstitutor(String content) {
            return Template.builder()
                    .type(TemplateType.STR_SUBSTITUTOR)
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
    @NonNull
    HttpRemoteCallSpec.HttpMethod method;
    @NonNull Template path;
    Map<String, List<Template>> headers;
    Template body;
    String contentType;
    Template responseMapper;
}
