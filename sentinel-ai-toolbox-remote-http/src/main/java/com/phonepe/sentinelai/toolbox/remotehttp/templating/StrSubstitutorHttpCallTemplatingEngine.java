package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

/**
 * Uses Apache String substitutor to convert {@link HttpCallTemplate.Template} to a string representation.
 */

public class StrSubstitutorHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    @Override
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        return StringSubstitutor.replace(template.getContent(), context);
    }
}
