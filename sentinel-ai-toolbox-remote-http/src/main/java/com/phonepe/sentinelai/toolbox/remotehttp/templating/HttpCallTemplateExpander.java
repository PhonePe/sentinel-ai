package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.TextHttpCallTemplatingEngine;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.TextSubstitutorHttpCallTemplatingEngine;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

/**
 * A class that expands a {@link HttpCallTemplate} to a {@link HttpCallSpec}.
 */
@AllArgsConstructor
public class HttpCallTemplateExpander {
    private final Map<HttpCallTemplate.TemplateType, HttpCallTemplatingEngine> templatingEngines;

    public HttpCallTemplateExpander() {
        this(Map.of(
                HttpCallTemplate.TemplateType.TEXT, new TextHttpCallTemplatingEngine(),
                HttpCallTemplate.TemplateType.TEXT_SUBSTITUTOR, new TextSubstitutorHttpCallTemplatingEngine()));
    }

    /**
     * Converts a {@link HttpCallTemplate} to a {@link HttpCallSpec}.
     *
     * @param template the template to convert
     * @param context  the context to use for conversion
     * @return the converted {@link HttpCallSpec}
     */
    public HttpCallSpec convert(final HttpCallTemplate template, Map<String, Object> context) {

        final var path = convert(template.getPath(), context);
        final var method = template.getMethod();
        final var headers =
                Objects.requireNonNullElseGet(template.getHeaders(),
                                              Map::<String, List<HttpCallTemplate.Template>>of)
                        .entrySet()
                        .stream()
                        .collect(toMap(Map.Entry::getKey,
                                       entry -> entry.getValue()
                                               .stream()
                                               .map(t -> convert(t, context))
                                               .toList()));
        final var body = convert(template.getBody(), context);
        final var contentType = template.getContentType();

        return HttpCallSpec.builder()
                .method(method)
                .path(path)
                .headers(headers)
                .body(body)
                .contentType(contentType)
                .build();
    }

    private String convert(final HttpCallTemplate.Template template, Map<String, Object> context) {
        if(null == template) {
            return null;
        }
        return Objects.requireNonNull(templatingEngines.get(template.getType()),
                                      "No templating engine found for type: " + template.getType())
                .convert(template, context);
    }
}
