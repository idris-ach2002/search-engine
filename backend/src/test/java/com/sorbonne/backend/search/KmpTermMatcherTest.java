package com.sorbonne.backend.search;

import com.sorbonne.backend.indexing.service.TextNormalizer;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.backend.search.service.KmpTermMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KmpTermMatcherTest {

    private final KmpTermMatcher matcher = new KmpTermMatcher(new TextNormalizer());

    @Test
    void reusesProjectOneKmpToMatchDictionarySubstrings() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "man"),
                new IndexedTerm(2, "mankind"),
                new IndexedTerm(3, "woman"),
                new IndexedTerm(4, "gentleman"),
                new IndexedTerm(5, "dog")
        );

        KmpTermMatcher.MatchResult result = matcher.match("MAN", terms);

        assertEquals("man", result.normalizedQuery());
        assertEquals(
                List.of("man", "mankind", "woman", "gentleman"),
                result.terms().stream().map(IndexedTerm::value).toList()
        );
    }

    @Test
    void normalizesAccentedKeywordBeforeKmp() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "cafe"),
                new IndexedTerm(2, "cafeteria"),
                new IndexedTerm(3, "tea")
        );

        KmpTermMatcher.MatchResult result = matcher.match("Café", terms);

        assertEquals("cafe", result.normalizedQuery());
        assertEquals(List.of("cafe", "cafeteria"), result.terms().stream().map(IndexedTerm::value).toList());
    }

    @Test
    void rejectsQueriesThatAreNotOneSimpleKeyword() {
        assertThrows(IllegalArgumentException.class, () -> matcher.match("   ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> matcher.match("the man", List.of()));
    }
}
