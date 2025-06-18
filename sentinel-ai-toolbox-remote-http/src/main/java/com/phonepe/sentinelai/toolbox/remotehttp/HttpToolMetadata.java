package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.*;

import java.util.Map;

/**
 * Metadata for a HTTP Tool.
 */
@Value
@Builder
@With
public class HttpToolMetadata {

    /**
     * Metadata for all the tool parameters
     */
    @Value
    @Builder
    @AllArgsConstructor
    public static class HttpToolParameterMeta {

        /**
         * A description for the parameter. Providing a good description will help the LLM send the correct values to it.
         */
        @NonNull
        String description;

        /**
         * Type of the parameter. Only simple types are supported for performance and simplicity.
         */
        @NonNull
        HttpToolParameterType type;
    }

    /**
     * The name of the tool being registered
     */
    @NonNull
    String name;

    /**
     * A detailed description of the tool. This will be passed to LLM and will be used by the LLM to select and use it
     */
    @NonNull
    String description;

    /**
     * List of parameters that the LLM needs to pass to the tool
     */
    Map<String, HttpToolParameterMeta> parameters;
}
