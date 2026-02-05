package com.phonepe.sentinelai.models;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class TokenCountingConfig {

    public static final TokenCountingConfig DEFAULT = TokenCountingConfig.builder()
        .messageOverHead(3)
        .nameOverhead(1)
        .assistantPrimingOverhead(3)
        .formattingOverhead(10)
        .build();

    /**
     * Overhead per message in tokens
     */
    int messageOverHead;

    /**
     * Overhead per name in tokens
     */
    int nameOverhead;

    /**
     * Overhead for system priming in tokens. Once every message
     */
    int assistantPrimingOverhead;
    /**
     * Overhead for formatting in tokens.
     * Once every message. Used for structued arguments to tool calls etc.
     */
    int formattingOverhead;
}
