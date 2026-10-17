package com.sorbonne.backend.ranking.model;

import java.time.Instant;
import java.util.List;

public final class PageRankResponses {
    private PageRankResponses() {}

    public record Rebuild(
            int nodeCount,
            int edgeCount,
            double dampingFactor,
            double tolerance,
            int maxIterations,
            int iterations,
            boolean converged,
            double finalDelta,
            double scoreSum,
            double minScore,
            double maxScore,
            long elapsedMillis
    ) {}

    public record Status(
            long readyBooks,
            long similarityEdges,
            long storedScores,
            boolean available,
            boolean stale,
            Instant computedAt,
            Instant similarityBuiltAt,
            double dampingFactor,
            double tolerance,
            int maxIterations,
            Integer iterations,
            Boolean converged,
            Double finalDelta,
            double lexicalWeight,
            double pageRankWeight
    ) {}

    public record RankedBook(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            double pageRankScore
    ) {}

    public record Top(List<RankedBook> books) {}
}
