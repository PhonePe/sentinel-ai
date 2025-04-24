package com.phonepe.sentinelai.core.tools;

/**
 *
 */
public interface ExecutableToolVisitor<T> {

    T visit(final ExternalTool externalTool);

    T visit(final InternalTool internalTool);
}
