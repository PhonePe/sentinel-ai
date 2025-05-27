package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines;

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

/**
 * Uses Apache String substitutor to convert {@link HttpCallTemplate.Template} to a string representation.
 */

public class TextSubstitutorHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    @Override
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        return StringSubstitutor.replace(template.getContent(), context);
    }
}
