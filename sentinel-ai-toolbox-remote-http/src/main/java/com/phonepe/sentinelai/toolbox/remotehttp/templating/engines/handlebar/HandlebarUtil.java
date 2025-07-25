package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.handlebar;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.GuavaTemplateCache;
import com.github.jknack.handlebars.io.TemplateSource;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@UtilityClass
public class HandlebarUtil {

    private static final Handlebars handlebars;

    /**
     * For use by Handlebars.java internally.
     */
    private static final Cache<TemplateSource, Template> templateCache = CacheBuilder
            .newBuilder()
            .build();


    private static final LoadingCache<String, Template> compilationCache = CacheBuilder
            .newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public Template load(String template) throws Exception {
                    return handlebars.compileInline(template);
                }
            });

    static {
        handlebars = new Handlebars()
                .with(new GuavaTemplateCache(templateCache));
    }

    public <H> void registerHelper(String name, Helper<H> helper) {
        handlebars.registerHelper(name, helper);
    }

    public String convert(final String content,
                          final Map<String, Object> context) throws ExecutionException, IOException {
        return compilationCache.get(content).apply(context);
    }
}
