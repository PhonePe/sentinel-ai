package com.phonepe.sentinelai.core.tools;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 *
 */
@Data
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ExecutableTool {
    private final ToolDefinition toolDefinition;

    public abstract <T> T accept(final ExecutableToolVisitor<T> visitor);

}
