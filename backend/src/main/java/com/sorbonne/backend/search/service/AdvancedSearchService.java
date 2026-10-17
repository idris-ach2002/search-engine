package com.sorbonne.backend.search.service;

import com.sorbonne.backend.ranking.service.SearchRankingService;
import com.sorbonne.backend.ranking.service.SearchRankingService.Candidate;
import com.sorbonne.backend.search.model.AdvancedSearchResponse;
import com.sorbonne.backend.search.model.SimpleSearchResult;
import com.sorbonne.backend.search.repository.SearchIndexRepository;
import com.sorbonne.backend.search.repository.SearchIndexRepository.BookPosting;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdvancedSearchService {
    private final SearchIndexRepository searchIndexRepository;
    private final RegexTermMatcher termMatcher;
    private final SearchRankingService rankingService;

    public AdvancedSearchService(
            SearchIndexRepository searchIndexRepository,
            RegexTermMatcher termMatcher,
            SearchRankingService rankingService
    ) {
        this.searchIndexRepository = searchIndexRepository;
        this.termMatcher = termMatcher;
        this.rankingService = rankingService;
    }

    @Transactional(readOnly = true)
    public AdvancedSearchResponse search(String regex, int page, int size) {
        long startedAt = System.nanoTime();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<IndexedTerm> dictionary = searchIndexRepository.loadTerms();
        RegexTermMatcher.MatchResult termMatches = termMatcher.match(regex, dictionary);
        List<Long> matchingTermIds = termMatches.terms().stream()
                .map(IndexedTerm::id)
                .toList();

        List<BookPosting> postings = searchIndexRepository.loadReadyBookPostings(matchingTermIds);
        SearchRankingService.RankedResults ranking = rankingService.rank(aggregate(postings));
        List<SimpleSearchResult> rankedResults = ranking.results();

        long totalElements = rankedResults.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + safeSize - 1) / safeSize);

        long offset = (long) safePage * safeSize;
        int fromIndex = (int) Math.min(offset, totalElements);
        int toIndex = (int) Math.min(offset + safeSize, totalElements);
        List<SimpleSearchResult> content = List.copyOf(rankedResults.subList(fromIndex, toIndex));

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new AdvancedSearchResponse(
                regex,
                termMatches.normalizedRegex(),
                safePage,
                safeSize,
                totalElements,
                totalPages,
                termMatches.terms().size(),
                termMatches.nfaStates(),
                termMatches.dfaStates(),
                termMatches.minimizedDfaStates(),
                ranking.pageRankApplied(),
                ranking.lexicalWeight(),
                ranking.pageRankWeight(),
                elapsedMillis,
                content
        );
    }

    private static List<Candidate> aggregate(List<BookPosting> postings) {
        Map<Long, Accumulator> byBook = new HashMap<>();
        for (BookPosting posting : postings) {
            Accumulator accumulator = byBook.computeIfAbsent(
                    posting.bookId(),
                    ignored -> new Accumulator(posting)
            );
            accumulator.add(posting);
        }

        List<Candidate> results = new ArrayList<>(byBook.size());
        for (Accumulator accumulator : byBook.values()) {
            results.add(accumulator.toCandidate());
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

        private Candidate toCandidate() {
            return new Candidate(
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
