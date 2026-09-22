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

import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MediaInput {
    MessageContentType contentType;
    String content;
    ImageDetail imageDetail;
    AudioFormat audioFormat;
    String fileId;
    String fileName;

    public static MediaInput audio(String content, AudioFormat audioFormat) {
        return MediaInput.builder()
                .contentType(MessageContentType.AUDIO)
                .content(content)
                .audioFormat(audioFormat)
                .build();
    }

    public static MediaInput fileContent(String content, String fileName) {
        return MediaInput.builder()
                .contentType(MessageContentType.FILE)
                .content(content)
                .fileName(fileName)
                .build();
    }

    public static MediaInput fileId(String fileId, String fileName) {
        return MediaInput.builder()
                .contentType(MessageContentType.FILE)
                .fileId(fileId)
                .fileName(fileName)
                .build();
    }

    public static MediaInput imageContent(String content, ImageDetail imageDetail) {
        return MediaInput.builder()
                .contentType(MessageContentType.IMAGE_DATA)
                .content(content)
                .imageDetail(imageDetail)
                .build();
    }

    public static MediaInput imageUrl(String url, ImageDetail imageDetail) {
        return MediaInput.builder()
                .contentType(MessageContentType.IMAGE_URL)
                .content(url)
                .imageDetail(imageDetail)
                .build();
    }

}
