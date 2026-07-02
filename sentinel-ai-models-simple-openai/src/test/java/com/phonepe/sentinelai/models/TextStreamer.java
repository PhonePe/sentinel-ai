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

package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agent.StreamConsumer;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.sentinelai.core.agent.Agent.OUTPUT_VARIABLE_NAME;

/**
 *
 */
@Slf4j
public class TextStreamer implements StreamConsumer {
    private final ObjectMapper mapper;
    private final ExecutorService executorService;
    private final StreamConsumer streamHandler;

    public TextStreamer(ObjectMapper mapper,
                        ExecutorService executorService,
                        StreamConsumer streamHandler) {
        this.mapper = mapper;
        this.executorService = executorService;
        this.streamHandler = streamHandler;
    }

    @Override
    @SneakyThrows
    public void consumeContent(final String content) {
        final var jfactory = mapper.getFactory();
        final var pipe = new PipedOutputStream();
        final var streamReader = new BufferedReader(new InputStreamReader(new PipedInputStream(pipe)));
        final var jsonParser = jfactory.createParser(streamReader);
        final var receivingOutput = new AtomicBoolean(false);
        executorService.submit(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                while (true) {
                    final var jsonToken = jsonParser.nextToken();
                    if (jsonToken == JsonToken.END_OBJECT) {
                        break;
                    }
                    final var field = jsonParser.currentName();
                    receivingOutput.set(field.equals(OUTPUT_VARIABLE_NAME));
                }
            }
        });
        try {
            pipe.write(content.getBytes());
        }
        catch (IOException e) {
            log.error("Error writing to parser");
        }
        if (receivingOutput.get()) {
            streamHandler.consumeContent(content);
        }
    }
}
