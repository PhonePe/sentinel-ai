package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

    @Test
    void testCreateMapper() {
        var mapper = JsonUtils.createMapper();
        assertNotNull(mapper);
        // Should not fail on unknown properties
        assertTrue(mapper.isEnabled(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS));
    }

    @Test
    void testEmptyWithNullNode() {
        assertTrue(JsonUtils.empty(null));
        assertTrue(JsonUtils.empty(NullNode.getInstance()));
        assertTrue(JsonUtils.empty(MissingNode.getInstance()));
    }

    @Test
    void testEmptyWithNonEmptyNode() {
        var mapper = JsonUtils.createMapper();
        final var node = mapper.createObjectNode();
        node.put("key", "value");
        assertFalse(JsonUtils.empty(node));
    }

    @Test
    void testEmptyWithEmptyObjectNode() {
        var mapper = JsonUtils.createMapper();
        final var node = mapper.createObjectNode();
        assertTrue(JsonUtils.empty(node));
    }

    @Test
    void testEmptyWithNonEmptyArrayNode() {
        var mapper = JsonUtils.createMapper();
        var arrayNode = mapper.createArrayNode();
        arrayNode.add("item");
        assertFalse(JsonUtils.empty(arrayNode));
    }

    @Test
    void testEmptyWithEmptyArrayNode() {
        var mapper = JsonUtils.createMapper();
        var arrayNode = mapper.createArrayNode();
        assertTrue(JsonUtils.empty(arrayNode));
    }

}
