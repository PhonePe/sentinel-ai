package com.phonepe.sentinelai.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.Builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public class HuggingFaceTokenizerModel implements TokenizerModel, AutoCloseable {
    private static final int MAX_LENGTH = 10_000;
    private final String modelName;
    private final String modelRoot;
    private final int maxLength;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean addSpecialTokens;
    private final boolean padding;

    @Builder
    public HuggingFaceTokenizerModel(String modelName, String modelRoot, int maxLength, boolean addSpecialTokens,
                                     boolean padding) {
        this.modelName = Objects.requireNonNullElse(modelName,
                                                    "sentence-transformers/all-MiniLM-L6-v2");
        this.modelRoot = Objects.requireNonNullElse(modelRoot,
                                                    "djl://ai.djl.huggingface.pytorch/");

        this.maxLength = Math.max(maxLength, MAX_LENGTH);
        this.tokenizer = HuggingFaceTokenizer.newInstance(this.modelName, getDJLConfig());
        this.addSpecialTokens = addSpecialTokens;
        this.padding = padding;
    }

    @Override
    public String[] tokenize(String input) {

        Encoding encoding = tokenizer.encode(input);
        return encoding.getTokens();
    }

    private Map<String, String> getDJLConfig() {
        final var options = new HashMap<String, String>();
        options.put("addSpecialTokens", Boolean.toString(addSpecialTokens));
        options.put("skipSpecialTokens", "true");
        options.put("padding", Boolean.toString(padding));
        final var maxLengthStr = Objects.toString(maxLength);
        options.put("modelMaxLength", maxLengthStr);
        options.put("maxLength", maxLengthStr);
        return options;
    }

    @Override
    public void close() {
        tokenizer.close();
    }
}
