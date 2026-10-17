package com.sorbonne.backend.similarity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimilarityPropertiesTest {
    private final SimilarityProperties p = new SimilarityProperties(2,0.10,3,200,3,2,0.05,20);
    @Test void keepsSmallSampleUsable() { assertEquals(3, p.resolveMaxDocumentFrequency(5)); }
    @Test void scalesRatioForFullCorpus() { assertEquals(167, p.resolveMaxDocumentFrequency(1664)); }
}
