package com.sorbonne.backend.search.model;

public record SimpleSearchResult(
        long bookId,
        int gutenbergId,
        String title,
        String author,
        String language,
        Integer wordCount,
        long lexicalScore,
        int matchedTerms,
        double normalizedLexicalScore,
        double pageRankScore,
        double normalizedPageRankScore,
        double finalScore
) {
}
