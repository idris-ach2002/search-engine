package com.sorbonne.backend.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingPropertiesTest {
    @Test
    void normalizesRankingWeights() {
        RankingProperties properties = new RankingProperties(0.85, 1e-10, 100, 85, 15);
        assertEquals(0.85, properties.normalizedLexicalWeight(), 1e-12);
        assertEquals(0.15, properties.normalizedPageRankWeight(), 1e-12);
    }

    @Test
    void rejectsInvalidDampingFactor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RankingProperties(1.0, 1e-10, 100, 0.85, 0.15)
        );
    }
}
