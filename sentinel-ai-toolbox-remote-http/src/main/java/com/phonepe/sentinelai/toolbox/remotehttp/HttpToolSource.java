package com.phonepe.sentinelai.toolbox.remotehttp;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Interface for a source of tools.
 */
public interface HttpToolSource<S extends HttpToolSource<S>> {

    /**
     * Register a tool for the given upstream.
     * @param upstream Upstream service identifier
     * @param tool Definition for the tool
     * @return this
     */
    default S register(String upstream, HttpTool ...tool) {
        Objects.requireNonNull(tool, "At least one tool is needed to be passed as argument");
        return register(upstream, Arrays.asList(tool));
    }

    /**
     * Register a tool for the given upstream.
     * @param upstream Upstream service identifier
     * @param tool Definition for the tool
     * @return this
     */
    S register(String upstream, List<HttpTool> tool);

    /**
     * List all known tools for the given upstream
     * @param upstream The upstream service identifier
     * @return List of tool configurations
     */
    List<HttpToolMetadata> list(String upstream);

    /**
     * Resolve a tool for the given upstream and tool name.
     * @param upstream The upstream service identifier
     * @param toolName The name of the tool being called
     * @param arguments The arguments for the tool as received from LLM
     * @return A fully resolved remote HTTP call specification
     * @throws IllegalArgumentException if the tool is not found for the given {upstream, toolName} combination
     */
    HttpRemoteCallSpec resolve(String upstream, String toolName, String arguments);
}
