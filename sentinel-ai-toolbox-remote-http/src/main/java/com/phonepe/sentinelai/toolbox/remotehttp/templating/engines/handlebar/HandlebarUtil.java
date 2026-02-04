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
                public Template load(String input) throws Exception {
                    return handlebars.compileInline(input);
                }
            });

    private static final Handlebars handlebars = new Handlebars()
            .with(new GuavaTemplateCache(templateCache));

    public <H> void registerHelper(String name, Helper<H> helper) {
        handlebars.registerHelper(name, helper);
    }

    public String convert(final String content,
                          final Map<String, Object> context) throws ExecutionException, IOException {
        return compilationCache.get(content).apply(context);
    }
}
