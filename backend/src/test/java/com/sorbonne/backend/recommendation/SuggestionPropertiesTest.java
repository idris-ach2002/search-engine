package com.sorbonne.backend.recommendation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuggestionPropertiesTest {
    private final SuggestionProperties properties = new SuggestionProperties(3, 10, 20, 5, 50);

    @Test
    void resolvesDefaultsAndExplicitValues() {
        assertEquals(3, properties.resolveSeedCount(null));
        assertEquals(4, properties.resolveSeedCount(4));
        assertEquals(10, properties.resolveLimit(null));
        assertEquals(25, properties.resolveLimit(25));
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> properties.resolveSeedCount(0));
        assertThrows(IllegalArgumentException.class, () -> properties.resolveSeedCount(6));
        assertThrows(IllegalArgumentException.class, () -> properties.resolveLimit(0));
        assertThrows(IllegalArgumentException.class, () -> properties.resolveLimit(51));
    }
}
