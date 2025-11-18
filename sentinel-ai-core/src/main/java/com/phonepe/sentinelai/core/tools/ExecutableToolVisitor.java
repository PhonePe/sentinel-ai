package com.phonepe.sentinelai.core.tools;

/**
 * To handle different types of ExecutableTool
 */
public interface ExecutableToolVisitor<T> {

    T visit(final ExternalTool externalTool);

    T visit(final InternalTool internalTool);
}
