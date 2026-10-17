package com.sorbonne.backend.search.repository;

import java.util.List;

public interface SearchIndexRepository {

    List<IndexedTerm> loadTerms();

    List<BookPosting> loadReadyBookPostings(List<Long> termIds);

    record IndexedTerm(long id, String value) {
    }

    record BookPosting(
            long bookId,
            int gutenbergId,
            String title,
            String author,
            String language,
            Integer wordCount,
            long termId,
            int occurrences
    ) {
    }
}
