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

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplatingEngine;

import lombok.SneakyThrows;

import java.util.Map;

public class HandlebarHttpCallTemplatingEngine implements HttpCallTemplatingEngine {

    @Override
    @SneakyThrows
    public String convert(HttpCallTemplate.Template template,
                          Map<String, Object> context) {
        return HandlebarUtil.convert(template.getContent(), context);
    }
}
