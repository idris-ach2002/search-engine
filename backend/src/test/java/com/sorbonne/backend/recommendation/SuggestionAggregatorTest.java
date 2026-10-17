package com.sorbonne.backend.recommendation;

import com.sorbonne.backend.recommendation.service.SuggestionAggregator;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionAggregatorTest {
    private final SuggestionAggregator aggregator = new SuggestionAggregator();

    @Test
    void combinesJaccardContributionsFromSeveralTopResults() {
        var seeds = List.of(
                new SuggestionAggregator.SeedInput(1, 101, "Seed A", "A", 1, 1.0),
                new SuggestionAggregator.SeedInput(2, 102, "Seed B", "B", 2, 0.5)
        );
        var neighbors = Map.of(
                1L, List.of(
                        neighbor(3, 103, "Shared", 0.60),
                        neighbor(4, 104, "Only A", 0.50),
                        neighbor(2, 102, "Seed B", 0.95)
                ),
                2L, List.of(
                        neighbor(3, 103, "Shared", 0.90),
                        neighbor(5, 105, "Only B", 0.40),
                        neighbor(1, 101, "Seed A", 0.95)
                )
        );

        var result = aggregator.aggregate(seeds, neighbors, 10);

        assertEquals(2, result.seeds().size());
        assertEquals(3, result.suggestions().size());
        assertEquals(3L, result.suggestions().getFirst().bookId());
        assertEquals(2, result.suggestions().getFirst().supportingSeeds());
        assertEquals(0.70, result.suggestions().getFirst().recommendationScore(), 1.0e-12);
        assertTrue(result.suggestions().stream().noneMatch(s -> s.bookId() == 1 || s.bookId() == 2));
    }

    @Test
    void usesEqualSeedWeightsWhenAllSearchScoresAreZero() {
        var seeds = List.of(
                new SuggestionAggregator.SeedInput(1, 101, "A", "A", 1, 0.0),
                new SuggestionAggregator.SeedInput(2, 102, "B", "B", 2, 0.0)
        );
        var result = aggregator.aggregate(seeds, Map.of(), 5);

        assertEquals(0.5, result.seeds().get(0).seedWeight(), 1.0e-12);
        assertEquals(0.5, result.seeds().get(1).seedWeight(), 1.0e-12);
    }

    private static SimilarityGraphRepository.Neighbor neighbor(long id, int gutenbergId, String title, double score) {
        return new SimilarityGraphRepository.Neighbor(id, gutenbergId, title, "Author", score, 10, 20);
    }
}
