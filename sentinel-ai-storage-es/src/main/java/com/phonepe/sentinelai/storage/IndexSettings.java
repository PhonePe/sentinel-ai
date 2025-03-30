package com.phonepe.sentinelai.storage;

import lombok.Builder;
import lombok.Value;

/**
 * Settings for index
 */
@Value
@Builder
public class IndexSettings {
    public static final int DEFAULT_SHARDS = 1;
    public static final int DEFAULT_REPLICAS = 0;
    public static final IndexSettings DEFAULT = new IndexSettings(DEFAULT_SHARDS, DEFAULT_REPLICAS);

    @Builder.Default
    int shards = DEFAULT_SHARDS;

    @Builder.Default
    int replicas = DEFAULT_REPLICAS;
}
