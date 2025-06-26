package com.phonepe.sentinelai.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.Builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A tokenizer model that uses models hosted on Hugging Face
 */
public class HuggingFaceTokenizerModel implements TokenizerModel, AutoCloseable {
    private static final int MAX_LENGTH = 10_000;
    private final String modelName;
    private final int maxLength;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean addSpecialTokens;
    private final boolean padding;

    @Builder
    public HuggingFaceTokenizerModel(
            String modelName,
            int maxLength,
            boolean addSpecialTokens,
            boolean padding) {
        System.setProperty("OPT_OUT_TRACKING", "true"); //DJL DIALS HOME ...
        this.modelName = Objects.requireNonNullElse(modelName,
                                                    "sentence-transformers/all-MiniLM-L6-v2");

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
