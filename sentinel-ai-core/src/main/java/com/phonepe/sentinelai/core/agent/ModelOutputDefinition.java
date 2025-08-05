package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/**
 *
 */
@Value
public class ModelOutputDefinition {
    String name;
    String description;
    JsonNode schema;
}
