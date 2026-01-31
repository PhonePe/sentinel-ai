package com.phonepe.sentinelai.core.model;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class ModelAttributes {

    public static final ModelAttributes DEFAULT_MODEL_ATTRIBUTES = ModelAttributes.builder()
            .contextWindowSize(128_000)
            .build();

    /**
     * Size of the context window for the model
     */
    int contextWindowSize;

}
