package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.phonepe.sentinelai.core.agent.Agent.OUTPUT_VARIABLE_NAME;

/**
 *
 */
@Slf4j
public class TextStreamer implements Consumer<byte[]> {
    private final ObjectMapper mapper;
    private final ExecutorService executorService;
    private final Consumer<byte[]> streamHandler;

    public TextStreamer(ObjectMapper mapper, ExecutorService executorService, Consumer<byte[]> streamHandler) {
        this.mapper = mapper;
        this.executorService = executorService;
        this.streamHandler = streamHandler;
    }

    @Override
    @SneakyThrows
    public void accept(byte[] data) {
        final var jfactory = mapper.getFactory();
        final var pipe = new PipedOutputStream();
        final var streamReader = new BufferedReader(new InputStreamReader(new PipedInputStream(pipe)));
        final var jsonParser = jfactory.createParser(streamReader);
        final var receivingOutput = new AtomicBoolean(false);
        executorService
                .submit(new Runnable() {
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
            pipe.write(data);
        }
        catch (IOException e) {
            log.error("Error writing to parser");
        }
        if (receivingOutput.get()) {
            streamHandler.accept(data);
        }
    }
}
