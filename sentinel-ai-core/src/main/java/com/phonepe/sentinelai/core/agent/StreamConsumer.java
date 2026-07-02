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

import com.google.common.base.Strings;

import javax.annotation.Nullable;

public interface StreamConsumer {
    public final StreamConsumer NO_OP = new StreamConsumer() {
        @Override
        public void consumeContent(final String content) {
            //Nothing to do here
        }

        @Override
        public void consumeReasoningAndContent(@Nullable final String reasoningData, @Nullable final String content) {
            //Nothing to do here
        }
    };

    /**
     * Consume content from the model output.
     *
     * Override this method if you want to consume only the content. By default, it does nothing.
     *
     * @param content The content from the model output.
     */
    default void consumeContent(final String content) {
        //Nothing to do here
    }

    /**
     * Consume reasoning and content from the model output.
     *
     * Override this method if you want to consume both reasoning and content. By default, it will only consume the
     * content.
     *
     * @param reasoningData The reasoning data from the model output.
     * @param content       The content from the model output.
     */
    default void consumeReasoningAndContent(@Nullable final String reasoningData, @Nullable final String content) {
        if (!Strings.isNullOrEmpty(content)) {
            consumeContent(content);
        }
    }
}
