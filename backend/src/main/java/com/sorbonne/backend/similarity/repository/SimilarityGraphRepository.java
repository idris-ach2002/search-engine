package com.sorbonne.backend.similarity.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SimilarityGraphRepository {
    long countReadyBooks();
    long countIndexedReadyBooks();
    long countEligibleTerms(int minDf, int maxDf, int minLength, Collection<String> excludedTerms);
    long countEligibleFeaturePostings(int minDf, int maxDf, int minLength, Collection<String> excludedTerms);
    Map<Long, Integer> loadFeatureCounts(int minDf, int maxDf, int minLength, Collection<String> excludedTerms);
    List<PairIntersection> loadPairIntersections(int minDf, int maxDf, int minLength, int minimumSharedTerms, Collection<String> excludedTerms);
    void replaceEdges(List<SimilarityEdge> edges);
    long countEdges();
    Instant lastBuiltAt();
    List<Neighbor> findNeighbors(long bookId, int limit);

    record PairIntersection(long bookAId, long bookBId, int intersectionSize) {}
    record SimilarityEdge(long bookAId, long bookBId, double score, int intersectionSize, int unionSize) {}
    record Neighbor(long bookId, int gutenbergId, String title, String author, double score, int intersectionSize, int unionSize) {}
}
