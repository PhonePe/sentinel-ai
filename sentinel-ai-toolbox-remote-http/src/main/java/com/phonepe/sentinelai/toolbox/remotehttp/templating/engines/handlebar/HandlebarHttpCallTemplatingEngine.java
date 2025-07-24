package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.handlebar;

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;
import lombok.SneakyThrows;
import lombok.val;

import java.util.Map;

public class HandlebarHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    @Override
    @SneakyThrows
    public String convert(HttpCallTemplate.Template template, Map<String, Object> context) {
        val handlebarTemplate = HandlebarUtil.getHandlebars().compileInline(template.getContent());
        return handlebarTemplate.apply(context);
    }
}
