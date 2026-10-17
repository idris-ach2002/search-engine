package com.sorbonne.backend.search.model;

import java.util.List;

public record SimpleSearchResponse(
        String query,
        String normalizedQuery,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int matchedIndexTerms,
        long elapsedMillis,
        List<SimpleSearchResult> content
) {
}
