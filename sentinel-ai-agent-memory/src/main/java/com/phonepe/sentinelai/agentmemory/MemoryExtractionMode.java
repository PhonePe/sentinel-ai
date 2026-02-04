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

package com.phonepe.sentinelai.agentmemory;

/**
 * Enum representing the mode of memory extraction.
 */
public enum MemoryExtractionMode {
    /**
     * Memory is extracted inline, meaning it is directly included in the response.
     */
    INLINE,
    /**
     * Memory is extracted out of band, meaning memory is extracted using a separate call to the LLM out of band.
     * In case of streaming execution of models, extraction is always out of band.
     */
    OUT_OF_BAND,
    /**
     * Memory is not extracted at all. This is useful in agents which work off memories gathered in some kind of
     * training mode and used in inference mode.
     */
    DISABLED
}
