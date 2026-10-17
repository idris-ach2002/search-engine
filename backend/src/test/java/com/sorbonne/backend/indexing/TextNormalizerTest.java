package com.sorbonne.backend.indexing;

import com.sorbonne.backend.indexing.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void stripsAccentsAndLowercasesToAscii() {
        assertEquals(
                List.of("elephant", "cafe", "naive", "oeuvre"),
                normalizer.tokenize("ÉLÉPHANT café naïve œuvre")
        );
    }

    @Test
    void keepsInternalApostrophesAndHyphens() {
        assertEquals(
                List.of("don't", "stop", "the", "well-known", "racket"),
                normalizer.tokenize("Don’t stop the well—known racket!")
        );
    }

    @Test
    void ignoresNumbersAndStandalonePunctuation() {
        assertEquals(
                List.of("book", "chapter", "hello", "world"),
                normalizer.tokenize("Book 10084 — Chapter 12: hello... world")
        );
    }
}
