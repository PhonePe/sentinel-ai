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

package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.Value;

import java.util.List;

/**
 * A list of facts to be passed to the agent.
 */
@Value
public class FactList {
    /**
     * A meaningful description of the facts being represented in the list of facts.
     * For example, "List of facts about the book"
     */
    String description;

    /**
     * A list of facts to be passed to the agent.
     */
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Fact> fact;
}
