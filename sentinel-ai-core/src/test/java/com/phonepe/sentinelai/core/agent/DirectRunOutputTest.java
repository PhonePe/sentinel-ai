package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link DirectRunOutput}.
 */
class DirectRunOutputTest {

    @Test
    void test() {
        final var objectMapper = JsonUtils.createMapper();
        final var success = DirectRunOutput.success(new ModelUsageStats(), objectMapper.createObjectNode());
        assertTrue(success.getData().isObject());
        assertNotNull(success.getError());
        assertEquals(SentinelError.success(), success.getError());

        final var error = DirectRunOutput.error(new ModelUsageStats(),
                                                SentinelError.error(ErrorType.JSON_ERROR, "Test error"));
        assertNull(error.getData());
        assertNotNull(error.getError());
    }
}