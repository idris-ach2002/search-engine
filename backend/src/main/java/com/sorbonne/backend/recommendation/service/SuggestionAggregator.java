package com.sorbonne.backend.recommendation.service;

import com.sorbonne.backend.recommendation.model.SuggestionResponses;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SuggestionAggregator {
    private static final Comparator<SuggestionResponses.Suggestion> ORDER =
            Comparator.comparingDouble(SuggestionResponses.Suggestion::recommendationScore).reversed()
                    .thenComparing(Comparator.comparingDouble(SuggestionResponses.Suggestion::bestJaccardScore).reversed())
                    .thenComparing(Comparator.comparingInt(SuggestionResponses.Suggestion::supportingSeeds).reversed())
                    .thenComparing(suggestion -> suggestion.title().toLowerCase(Locale.ROOT))
                    .thenComparingLong(SuggestionResponses.Suggestion::bookId);

    public Result aggregate(
            List<SeedInput> seeds,
            Map<Long, List<SimilarityGraphRepository.Neighbor>> neighborsBySeed,
            int limit
    ) {
        if (seeds == null || seeds.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        double positiveScoreSum = seeds.stream()
                .mapToDouble(seed -> Math.max(0.0, seed.finalScore()))
                .sum();

        Map<Long, Double> weights = new LinkedHashMap<>();
        List<SuggestionResponses.Seed> weightedSeeds = new ArrayList<>(seeds.size());
        for (SeedInput seed : seeds) {
            double weight = positiveScoreSum > 0.0
                    ? Math.max(0.0, seed.finalScore()) / positiveScoreSum
                    : 1.0 / seeds.size();
            weights.put(seed.bookId(), weight);
            weightedSeeds.add(new SuggestionResponses.Seed(
                    seed.bookId(),
                    seed.gutenbergId(),
                    seed.title(),
                    seed.author(),
                    seed.searchRank(),
                    seed.finalScore(),
                    weight
            ));
        }

        Set<Long> seedIds = new HashSet<>(weights.keySet());
        Map<Long, Accumulator> candidates = new HashMap<>();

        for (SeedInput seed : seeds) {
            double weight = weights.get(seed.bookId());
            for (SimilarityGraphRepository.Neighbor neighbor : neighborsBySeed.getOrDefault(seed.bookId(), List.of())) {
                if (seedIds.contains(neighbor.bookId())) {
                    continue;
                }

                double contribution = weight * neighbor.score();
                Accumulator accumulator = candidates.computeIfAbsent(
                        neighbor.bookId(),
                        ignored -> new Accumulator(neighbor)
                );
                accumulator.add(seed, weight, neighbor.score(), contribution);
            }
        }

        List<SuggestionResponses.Suggestion> suggestions = candidates.values().stream()
                .map(Accumulator::toSuggestion)
                .sorted(ORDER)
                .limit(limit)
                .toList();

        return new Result(List.copyOf(weightedSeeds), suggestions);
    }

    public record SeedInput(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            int searchRank,
            double finalScore
    ) {
    }

    public record Result(
            List<SuggestionResponses.Seed> seeds,
            List<SuggestionResponses.Suggestion> suggestions
    ) {
    }

    private static final class Accumulator {
        private final long bookId;
        private final int gutenbergId;
        private final String title;
        private final String author;
        private double recommendationScore;
        private double bestJaccardScore;
        private final List<SuggestionResponses.Reason> reasons = new ArrayList<>();

        private Accumulator(SimilarityGraphRepository.Neighbor neighbor) {
            this.bookId = neighbor.bookId();
            this.gutenbergId = neighbor.gutenbergId();
            this.title = neighbor.title();
            this.author = neighbor.author();
        }

        private void add(SeedInput seed, double seedWeight, double jaccardScore, double contribution) {
            recommendationScore += contribution;
            bestJaccardScore = Math.max(bestJaccardScore, jaccardScore);
            reasons.add(new SuggestionResponses.Reason(
                    seed.bookId(),
                    seed.title(),
                    seedWeight,
                    jaccardScore,
                    contribution
            ));
        }

        private SuggestionResponses.Suggestion toSuggestion() {
            List<SuggestionResponses.Reason> orderedReasons = reasons.stream()
                    .sorted(Comparator.comparingDouble(SuggestionResponses.Reason::contribution).reversed())
                    .toList();
            return new SuggestionResponses.Suggestion(
                    bookId,
                    gutenbergId,
                    title,
                    author,
                    recommendationScore,
                    bestJaccardScore,
                    orderedReasons.size(),
                    orderedReasons
            );
        }
    }
}
