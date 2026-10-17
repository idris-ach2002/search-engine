package com.sorbonne.backend.search.service;

import com.sorbonne.backend.indexing.service.TextNormalizer;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.PreparedSearch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KmpTermMatcher {

    private final TextNormalizer textNormalizer;

    public KmpTermMatcher(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    /**
     * Matches the simple keyword against the indexed term dictionary with the
     * Project 1 KMP implementation. The pattern is prepared once per request.
     */
    public MatchResult match(String query, List<IndexedTerm> terms) {
        String normalizedQuery = normalizeKeyword(query);
        PreparedSearch prepared = KMPSearch.prepare(normalizedQuery);

        List<IndexedTerm> matches = terms.stream()
                .filter(term -> prepared.search(term.value()))
                .toList();

        return new MatchResult(normalizedQuery, matches);
    }

    private String normalizeKeyword(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        List<String> tokens = textNormalizer.tokenize(query);
        if (tokens.size() != 1) {
            throw new IllegalArgumentException("Simple search accepts exactly one keyword");
        }
        return tokens.getFirst();
    }

    public record MatchResult(String normalizedQuery, List<IndexedTerm> terms) {
    }
}
