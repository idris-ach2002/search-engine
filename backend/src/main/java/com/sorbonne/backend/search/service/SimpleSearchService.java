package com.sorbonne.backend.search.service;

import com.sorbonne.backend.search.model.SimpleSearchResponse;
import com.sorbonne.backend.search.model.SimpleSearchResult;
import com.sorbonne.backend.search.repository.SearchIndexRepository;
import com.sorbonne.backend.search.repository.SearchIndexRepository.BookPosting;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SimpleSearchService {

    private static final Comparator<SimpleSearchResult> RESULT_ORDER =
            Comparator.comparingLong(SimpleSearchResult::lexicalScore).reversed()
                    .thenComparing(Comparator.comparingInt(SimpleSearchResult::matchedTerms).reversed())
                    .thenComparing(result -> result.title().toLowerCase(Locale.ROOT))
                    .thenComparingLong(SimpleSearchResult::bookId);

    private final SearchIndexRepository searchIndexRepository;
    private final KmpTermMatcher termMatcher;

    public SimpleSearchService(
            SearchIndexRepository searchIndexRepository,
            KmpTermMatcher termMatcher
    ) {
        this.searchIndexRepository = searchIndexRepository;
        this.termMatcher = termMatcher;
    }

    @Transactional(readOnly = true)
    public SimpleSearchResponse search(String query, int page, int size) {
        long startedAt = System.nanoTime();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<IndexedTerm> dictionary = searchIndexRepository.loadTerms();
        KmpTermMatcher.MatchResult termMatches = termMatcher.match(query, dictionary);
        List<Long> matchingTermIds = termMatches.terms().stream()
                .map(IndexedTerm::id)
                .toList();

        List<BookPosting> postings = searchIndexRepository.loadReadyBookPostings(matchingTermIds);
        List<SimpleSearchResult> rankedResults = aggregate(postings);
        rankedResults.sort(RESULT_ORDER);

        long totalElements = rankedResults.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + safeSize - 1) / safeSize);

        long offset = (long) safePage * safeSize;
        int fromIndex = (int) Math.min(offset, totalElements);
        int toIndex = (int) Math.min(offset + safeSize, totalElements);
        List<SimpleSearchResult> content = List.copyOf(rankedResults.subList(fromIndex, toIndex));

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new SimpleSearchResponse(
                query,
                termMatches.normalizedQuery(),
                safePage,
                safeSize,
                totalElements,
                totalPages,
                termMatches.terms().size(),
                elapsedMillis,
                content
        );
    }

    private static List<SimpleSearchResult> aggregate(List<BookPosting> postings) {
        Map<Long, Accumulator> byBook = new HashMap<>();

        for (BookPosting posting : postings) {
            Accumulator accumulator = byBook.computeIfAbsent(
                    posting.bookId(),
                    ignored -> new Accumulator(posting)
            );
            accumulator.add(posting);
        }

        List<SimpleSearchResult> results = new ArrayList<>(byBook.size());
        for (Accumulator accumulator : byBook.values()) {
            results.add(accumulator.toResult());
        }
        return results;
    }

    private static final class Accumulator {
        private final long bookId;
        private final int gutenbergId;
        private final String title;
        private final String author;
        private final String language;
        private final Integer wordCount;
        private long score;
        private int matchedTerms;

        private Accumulator(BookPosting posting) {
            this.bookId = posting.bookId();
            this.gutenbergId = posting.gutenbergId();
            this.title = posting.title();
            this.author = posting.author();
            this.language = posting.language();
            this.wordCount = posting.wordCount();
        }

        private void add(BookPosting posting) {
            score += posting.occurrences();
            matchedTerms++;
        }

        private SimpleSearchResult toResult() {
            return new SimpleSearchResult(
                    bookId,
                    gutenbergId,
                    title,
                    author,
                    language,
                    wordCount,
                    score,
                    matchedTerms
            );
        }
    }
}
