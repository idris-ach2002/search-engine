package com.sorbonne.backend.ranking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PageRankRepository {
    List<Long> loadReadyBookIds();
    List<WeightedEdge> loadSimilarityEdges();
    void replaceScores(Map<Long, Double> scores, RunState state);
    long countScores();
    long countSimilarityEdges();
    Optional<RunState> loadState();
    Instant lastSimilarityBuiltAt();
    List<RankedBook> topScores(int limit);

    record WeightedEdge(long bookAId, long bookBId, double weight) {}

    record RunState(
            Instant computedAt,
            double dampingFactor,
            double tolerance,
            int maxIterations,
            int iterations,
            boolean converged,
            double finalDelta,
            int nodeCount,
            int edgeCount
    ) {}

    record RankedBook(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            double score
    ) {}
}
