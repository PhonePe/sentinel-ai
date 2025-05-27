package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines;

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;

import java.util.Map;

/**
 * This is a simple templating engine that does not do any templating.
 */
public class TextHttpCallTemplatingEngine implements HttpCallTemplatingEngine {
    @Override
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        return template.getContent();
    }
}
