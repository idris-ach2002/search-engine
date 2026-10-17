package com.sorbonne.backend.search.model;

import java.util.List;

public record AdvancedSearchResponse(
        String regex,
        String normalizedRegex,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int matchedIndexTerms,
        int nfaStates,
        int dfaStates,
        int minimizedDfaStates,
        boolean pageRankApplied,
        double lexicalWeight,
        double pageRankWeight,
        long elapsedMillis,
        List<SimpleSearchResult> content
) {
}
