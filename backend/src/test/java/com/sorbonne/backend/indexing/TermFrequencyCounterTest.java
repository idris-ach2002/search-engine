package com.sorbonne.backend.indexing;

import com.sorbonne.backend.indexing.service.TermFrequencyCounter;
import com.sorbonne.backend.indexing.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermFrequencyCounterTest {

    private final TermFrequencyCounter counter = new TermFrequencyCounter(new TextNormalizer());

    @Test
    void countsNormalizedTerms() {
        assertEquals(
                Map.of("dog", 3, "cafe", 2, "cat", 1),
                counter.count("Dog dog DOG, café CAFE cat.")
        );
    }
}
