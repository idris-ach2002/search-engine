package com.sorbonne.backend.similarity.model;

import java.time.Instant;
import java.util.List;

public final class SimilarityResponses {
    private SimilarityResponses() {}

    public record Rebuild(long readyBooks, long indexedBooks, int maxDocumentFrequency, long eligibleTerms,
                          long featurePostings, int candidatePairs, int qualifyingPairs, int storedEdges,
                          double minimumSimilarity, int maxNeighborsPerBook, long elapsedMillis) {}

    public record Status(long readyBooks, long indexedBooks, long storedEdges, Instant lastBuiltAt,
                         int minDocumentFrequency, double maxDocumentFrequencyRatio,
                         double minimumSimilarity, int maxNeighborsPerBook) {}

    public record Neighbor(long bookId, int gutenbergId, String title, String author,
                           double jaccardScore, int intersectionSize, int unionSize) {}

    public record Neighbors(long bookId, List<Neighbor> neighbors) {}
}
