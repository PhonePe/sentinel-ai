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

package com.phonepe.sentinelai.core.agentmessages.requests;

import com.google.common.base.Preconditions;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * User prompt/request sent from user to LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserPrompt extends AgentRequest {
    MessageContentType contentType;
    String content;
    boolean compacted;
    ImageDetail imageDetail;
    AudioFormat audioFormat;
    String fileId;
    String fileName;
    LocalDateTime sentAt;

    @Builder
    @Jacksonized
    public UserPrompt(String sessionId,
                      String runId,
                      String messageId,
                      Long timestamp,
                      MessageContentType contentType,
                      @NonNull String content,
                      boolean compacted,
                      ImageDetail imageDetail,
                      AudioFormat audioFormat,
                      String fileId,
                      String fileName,
                      LocalDateTime sentAt) {
        super(AgentMessageType.USER_PROMPT_REQUEST_MESSAGE,
              sessionId,
              runId,
              messageId,
              timestamp);
        this.contentType = Objects.requireNonNullElse(contentType, MessageContentType.TEXT);
        this.content = content;
        this.compacted = compacted;
        this.imageDetail = Objects.requireNonNullElse(imageDetail, ImageDetail.AUTO);
        this.audioFormat = Objects.requireNonNullElse(audioFormat, AudioFormat.WAV);
        this.fileId = fileId;
        this.fileName = fileName;
        this.sentAt = Objects.requireNonNullElse(sentAt, LocalDateTime.now(ZoneId.systemDefault()));
    }

    public static UserPrompt audio(String sessionId,
                                   String runId,
                                   String content,
                                   AudioFormat audioFormat,
                                   LocalDateTime sentAt) {
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.AUDIO,
                              content,
                              false,
                              ImageDetail.AUTO,
                              audioFormat,
                              null,
                              null,
                              sentAt);
    }

    public static UserPrompt compactedText(String sessionId, String runId, String content, LocalDateTime sentAt) {
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.TEXT,
                              content,
                              true,
                              ImageDetail.AUTO,
                              AudioFormat.WAV,
                              null,
                              null,
                              sentAt);
    }

    public static UserPrompt file(String sessionId,
                                  String runId,
                                  String content,
                                  String fileId,
                                  String fileName,
                                  LocalDateTime sentAt) {
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.FILE,
                              content,
                              false,
                              ImageDetail.AUTO,
                              AudioFormat.WAV,
                              fileId,
                              fileName,
                              sentAt);
    }

    public static UserPrompt imageData(String sessionId,
                                       String runId,
                                       String content,
                                       ImageDetail imageDetail,
                                       LocalDateTime sentAt) {
        Preconditions.checkArgument(content.matches("^data:image/(png|jpeg|jpg);base64,([A-Za-z0-9+/=]+)$"),
                                    "Image data should be of the format data:image/{png|jpeg};base64,<actual base64 image content>");
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.IMAGE_DATA,
                              content,
                              false,
                              imageDetail,
                              AudioFormat.WAV,
                              null,
                              null,
                              sentAt);
    }

    public static UserPrompt imageURL(String sessionId,
                                      String runId,
                                      URL url,
                                      ImageDetail imageDetail,
                                      LocalDateTime sentAt) {
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.IMAGE_URL,
                              url.toString(),
                              false,
                              imageDetail,
                              AudioFormat.WAV,
                              null,
                              null,
                              sentAt);
    }

    public static UserPrompt text(String sessionId, String runId, String content, LocalDateTime sentAt) {
        return new UserPrompt(sessionId,
                              runId,
                              null,
                              null,
                              MessageContentType.TEXT,
                              content,
                              false,
                              ImageDetail.AUTO,
                              AudioFormat.WAV,
                              null,
                              null,
                              sentAt);
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
