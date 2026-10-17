package com.sorbonne.backend.search;

import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.backend.search.service.RegexPatternNormalizer;
import com.sorbonne.backend.search.service.RegexTermMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexTermMatcherTest {

    private final RegexTermMatcher matcher = new RegexTermMatcher(new RegexPatternNormalizer());

    @Test
    void compilesProjectOnePipelineAndMatchesDictionarySubstrings() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "cat"),
                new IndexedTerm(2, "cot"),
                new IndexedTerm(3, "scatter"),
                new IndexedTerm(4, "cut"),
                new IndexedTerm(5, "dog")
        );

        RegexTermMatcher.MatchResult result = matcher.match("c(a|o)t", terms);

        assertEquals("c(a|o)t", result.normalizedRegex());
        assertEquals(
                List.of("cat", "cot", "scatter"),
                result.terms().stream().map(IndexedTerm::value).toList()
        );
        assertTrue(result.nfaStates() > 0);
        assertTrue(result.dfaStates() > 0);
        assertTrue(result.minimizedDfaStates() > 0);
        assertTrue(result.minimizedDfaStates() <= result.dfaStates());
    }

    @Test
    void supportsStarWildcardAndAccentNormalization() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "cafe"),
                new IndexedTerm(2, "cafeteria"),
                new IndexedTerm(3, "coffee")
        );

        RegexTermMatcher.MatchResult result = matcher.match("CAFÉ.*", terms);

        assertEquals("cafe.*", result.normalizedRegex());
        assertEquals(
                List.of("cafe", "cafeteria"),
                result.terms().stream().map(IndexedTerm::value).toList()
        );
    }

    @Test
    void rejectsUnsupportedOrMalformedExpressions() {
        assertThrows(IllegalArgumentException.class, () -> matcher.match("cat+", List.of()));
        assertThrows(IllegalArgumentException.class, () -> matcher.match("(cat", List.of()));
    }
}
