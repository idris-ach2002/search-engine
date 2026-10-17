package com.sorbonne.backend.ranking.service;

import com.sorbonne.backend.ranking.RankingProperties;
import com.sorbonne.backend.ranking.repository.SearchRankRepository;
import com.sorbonne.backend.search.model.SimpleSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SearchRankingService {
    private static final Comparator<SimpleSearchResult> RESULT_ORDER =
            Comparator.comparingDouble(SimpleSearchResult::finalScore).reversed()
                    .thenComparing(Comparator.comparingLong(SimpleSearchResult::lexicalScore).reversed())
                    .thenComparing(Comparator.comparingInt(SimpleSearchResult::matchedTerms).reversed())
                    .thenComparing(result -> result.title().toLowerCase(Locale.ROOT))
                    .thenComparingLong(SimpleSearchResult::bookId);

    private final SearchRankRepository rankRepository;
    private final RankingProperties properties;

    public SearchRankingService(SearchRankRepository rankRepository, RankingProperties properties) {
        this.rankRepository = rankRepository;
        this.properties = properties;
    }

    public RankedResults rank(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new RankedResults(
                    List.of(),
                    false,
                    1.0,
                    0.0
            );
        }

        long maxLexical = candidates.stream().mapToLong(Candidate::lexicalScore).max().orElse(0L);
        boolean pageRankApplied = rankRepository.hasCurrentScores();
        double maxPageRank = pageRankApplied ? rankRepository.maxScore() : 0.0;
        if (maxPageRank <= 0.0) {
            pageRankApplied = false;
        }

        Collection<Long> bookIds = candidates.stream().map(Candidate::bookId).toList();
        Map<Long, Double> pageRanks = pageRankApplied
                ? rankRepository.loadScores(bookIds)
                : Map.of();

        double lexicalWeight = pageRankApplied ? properties.normalizedLexicalWeight() : 1.0;
        double pageRankWeight = pageRankApplied ? properties.normalizedPageRankWeight() : 0.0;

        List<SimpleSearchResult> results = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            double normalizedLexical = maxLexical == 0
                    ? 0.0
                    : (double) candidate.lexicalScore() / maxLexical;
            double pageRank = pageRanks.getOrDefault(candidate.bookId(), 0.0);
            double normalizedPageRank = pageRankApplied && maxPageRank > 0.0
                    ? pageRank / maxPageRank
                    : 0.0;
            double finalScore = lexicalWeight * normalizedLexical
                    + pageRankWeight * normalizedPageRank;

            results.add(new SimpleSearchResult(
                    candidate.bookId(),
                    candidate.gutenbergId(),
                    candidate.title(),
                    candidate.author(),
                    candidate.language(),
                    candidate.wordCount(),
                    candidate.lexicalScore(),
                    candidate.matchedTerms(),
                    normalizedLexical,
                    pageRank,
                    normalizedPageRank,
                    finalScore
            ));
        }

        results.sort(RESULT_ORDER);
        return new RankedResults(List.copyOf(results), pageRankApplied, lexicalWeight, pageRankWeight);
    }

    public record Candidate(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            String language,
            Integer wordCount,
            long lexicalScore,
            int matchedTerms
    ) {}

    public record RankedResults(
            List<SimpleSearchResult> results,
            boolean pageRankApplied,
            double lexicalWeight,
            double pageRankWeight
    ) {}
}
