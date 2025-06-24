package configuredagents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleCache}.
 */
class SimpleCacheTest {

    private record TestObject(String name) {
        public static TestObject create(String name) {
            return new TestObject(name);
        }
    }
    @Test
    void test() {
        final var cache = new SimpleCache<>(TestObject::create);
        final var res = cache.find("test");
        assertTrue(res.isPresent());
        assertEquals("test", res.get().name());

        assertThrowsExactly(NullPointerException.class, () -> cache.find(null));
    }
}