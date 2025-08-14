package com.phonepe.sentinelai.core.model;

import java.util.function.UnaryOperator;

/**
 *
 */
public final class IdentityOutputGenerator implements UnaryOperator<String> {

    @Override
    public String apply(final String content) {
        return content;
    }
}
