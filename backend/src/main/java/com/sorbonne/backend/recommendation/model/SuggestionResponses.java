package com.sorbonne.backend.recommendation.model;

import java.util.List;

public final class SuggestionResponses {
    private SuggestionResponses() {
    }

    public record Seed(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            int searchRank,
            double finalScore,
            double seedWeight
    ) {
    }

    public record Reason(
            long seedBookId,
            String seedTitle,
            double seedWeight,
            double jaccardScore,
            double contribution
    ) {
    }

    public record Suggestion(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            double recommendationScore,
            double bestJaccardScore,
            int supportingSeeds,
            List<Reason> reasons
    ) {
    }

    public record SearchSuggestions(
            String mode,
            String input,
            long sourceSearchTotalElements,
            int requestedSeedCount,
            int usedSeedCount,
            int limit,
            long elapsedMillis,
            List<Seed> seeds,
            List<Suggestion> suggestions
    ) {
    }

    public record BookSuggestion(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            double jaccardScore,
            int intersectionSize,
            int unionSize
    ) {
    }

    public record BookSuggestions(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            int limit,
            List<BookSuggestion> suggestions
    ) {
    }
}
