package com.sorbonne.backend.search;

import com.sorbonne.backend.search.service.RegexPatternNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegexPatternNormalizerTest {

    private final RegexPatternNormalizer normalizer = new RegexPatternNormalizer();

    @Test
    void normalizesCaseAndAccentsWithoutChangingRegexOperators() {
        assertEquals("CAF(E|A).*", normalizer.normalize("CAF(É|A).*") );
    }

    @Test
    void preservesJavaRegexEscapesAndCaseSensitiveUnicodePropertyNames() {
        assertEquals("CAF\\p{Lu}+", normalizer.normalize("CAF\\p{Lu}+"));
        assertEquals("(?U)[A-Z]+", normalizer.normalize("(?U)[A-Z]+"));
    }

    @Test
    void rejectsBlankAndOversizedPatterns() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("a".repeat(257)));
    }
}
