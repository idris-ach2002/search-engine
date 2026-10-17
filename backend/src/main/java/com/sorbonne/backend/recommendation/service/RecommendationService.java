package com.sorbonne.backend.recommendation.service;

import com.sorbonne.backend.book.Book;
import com.sorbonne.backend.book.BookRepository;
import com.sorbonne.backend.book.BookStatus;
import com.sorbonne.backend.recommendation.SuggestionProperties;
import com.sorbonne.backend.recommendation.model.SuggestionResponses;
import com.sorbonne.backend.search.model.AdvancedSearchResponse;
import com.sorbonne.backend.search.model.SimpleSearchResponse;
import com.sorbonne.backend.search.model.SimpleSearchResult;
import com.sorbonne.backend.search.service.AdvancedSearchService;
import com.sorbonne.backend.search.service.SimpleSearchService;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {
    private final SimpleSearchService simpleSearchService;
    private final AdvancedSearchService advancedSearchService;
    private final SimilarityGraphRepository similarityRepository;
    private final BookRepository bookRepository;
    private final SuggestionProperties properties;
    private final SuggestionAggregator aggregator;

    public RecommendationService(
            SimpleSearchService simpleSearchService,
            AdvancedSearchService advancedSearchService,
            SimilarityGraphRepository similarityRepository,
            BookRepository bookRepository,
            SuggestionProperties properties,
            SuggestionAggregator aggregator
    ) {
        this.simpleSearchService = simpleSearchService;
        this.advancedSearchService = advancedSearchService;
        this.similarityRepository = similarityRepository;
        this.bookRepository = bookRepository;
        this.properties = properties;
        this.aggregator = aggregator;
    }

    @Transactional(readOnly = true)
    public SuggestionResponses.SearchSuggestions simpleSearchSuggestions(
            String query,
            Integer requestedSeedCount,
            Integer requestedLimit
    ) {
        int seedCount = properties.resolveSeedCount(requestedSeedCount);
        int limit = properties.resolveLimit(requestedLimit);
        long startedAt = System.nanoTime();

        SimpleSearchResponse search = simpleSearchService.search(query, 0, seedCount);
        SuggestionAggregator.Result aggregated = aggregate(search.content(), limit);

        return new SuggestionResponses.SearchSuggestions(
                "simple",
                search.query(),
                search.totalElements(),
                seedCount,
                aggregated.seeds().size(),
                limit,
                elapsedMillis(startedAt),
                aggregated.seeds(),
                aggregated.suggestions()
        );
    }

    @Transactional(readOnly = true)
    public SuggestionResponses.SearchSuggestions advancedSearchSuggestions(
            String regex,
            Integer requestedSeedCount,
            Integer requestedLimit
    ) {
        int seedCount = properties.resolveSeedCount(requestedSeedCount);
        int limit = properties.resolveLimit(requestedLimit);
        long startedAt = System.nanoTime();

        AdvancedSearchResponse search = advancedSearchService.search(regex, 0, seedCount);
        SuggestionAggregator.Result aggregated = aggregate(search.content(), limit);

        return new SuggestionResponses.SearchSuggestions(
                "advanced",
                search.regex(),
                search.totalElements(),
                seedCount,
                aggregated.seeds().size(),
                limit,
                elapsedMillis(startedAt),
                aggregated.seeds(),
                aggregated.suggestions()
        );
    }

    @Transactional(readOnly = true)
    public SuggestionResponses.BookSuggestions bookSuggestions(long bookId, Integer requestedLimit) {
        int limit = properties.resolveLimit(requestedLimit);
        Book book = bookRepository.findById(bookId)
                .filter(candidate -> candidate.getStatus() == BookStatus.READY)
                .orElseThrow(() -> new IllegalArgumentException("Unknown READY book id: " + bookId));

        List<SuggestionResponses.BookSuggestion> suggestions = similarityRepository.findNeighbors(bookId, limit).stream()
                .map(neighbor -> new SuggestionResponses.BookSuggestion(
                        neighbor.bookId(),
                        neighbor.gutenbergId(),
                        neighbor.title(),
                        neighbor.author(),
                        neighbor.score(),
                        neighbor.intersectionSize(),
                        neighbor.unionSize()
                ))
                .toList();

        return new SuggestionResponses.BookSuggestions(
                bookId,
                book.getGutenbergId(),
                book.getTitle(),
                book.getAuthor(),
                limit,
                suggestions
        );
    }

    private SuggestionAggregator.Result aggregate(List<SimpleSearchResult> rankedSeeds, int limit) {
        List<SuggestionAggregator.SeedInput> seeds = new ArrayList<>(rankedSeeds.size());
        for (int index = 0; index < rankedSeeds.size(); index++) {
            SimpleSearchResult result = rankedSeeds.get(index);
            seeds.add(new SuggestionAggregator.SeedInput(
                    result.bookId(),
                    result.gutenbergId(),
                    result.title(),
                    result.author(),
                    index + 1,
                    result.finalScore()
            ));
        }

        Map<Long, List<SimilarityGraphRepository.Neighbor>> neighborsBySeed = new LinkedHashMap<>();
        int neighborFetchLimit = Math.max(limit, properties.neighborsPerSeed());
        for (SuggestionAggregator.SeedInput seed : seeds) {
            neighborsBySeed.put(
                    seed.bookId(),
                    similarityRepository.findNeighbors(seed.bookId(), Math.min(neighborFetchLimit, 100))
            );
        }

        return aggregator.aggregate(seeds, neighborsBySeed, limit);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
