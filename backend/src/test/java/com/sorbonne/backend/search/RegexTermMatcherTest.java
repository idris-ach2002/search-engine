package com.sorbonne.backend.search;

import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.backend.search.service.RegexPatternNormalizer;
import com.sorbonne.backend.search.service.RegexTermMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegexTermMatcherTest {

    private final RegexTermMatcher matcher = new RegexTermMatcher(new RegexPatternNormalizer());

    @Test
    void compilesJavaPatternAndMatchesDictionarySubstrings() {
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
    }

    @Test
    void supportsStarWildcardAndAccentNormalization() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "cafe"),
                new IndexedTerm(2, "cafeteria"),
                new IndexedTerm(3, "coffee")
        );

        RegexTermMatcher.MatchResult result = matcher.match("CAFÉ.*", terms);

        assertEquals("CAFE.*", result.normalizedRegex());
        assertEquals(
                List.of("cafe", "cafeteria"),
                result.terms().stream().map(IndexedTerm::value).toList()
        );
    }

    @Test
    void supportsStandardJavaRegexFeatures() {
        List<IndexedTerm> terms = List.of(
                new IndexedTerm(1, "cat"),
                new IndexedTerm(2, "catt"),
                new IndexedTerm(3, "catalogue"),
                new IndexedTerm(4, "dog")
        );
        assertEquals(List.of("cat", "catt"), matcher.match("^cat+$", terms)
                .terms().stream().map(IndexedTerm::value).toList());
        assertEquals(List.of("cat", "catt", "catalogue"), matcher.match("^cat[a-z]*", terms)
                .terms().stream().map(IndexedTerm::value).toList());
    }

    @Test
    void preservesEscapesAndRejectsInvalidExpressions() {
        List<IndexedTerm> terms = List.of(new IndexedTerm(1, "cat"), new IndexedTerm(2, "catalogue"));
        assertEquals(List.of("cat"), matcher.match("\\bcat\\b", terms)
                .terms().stream().map(IndexedTerm::value).toList());
        assertEquals(List.of("cat", "catalogue"), matcher.match("CAT\\p{Ll}*", terms)
                .terms().stream().map(IndexedTerm::value).toList());
        assertThrows(IllegalArgumentException.class, () -> matcher.match("(cat", List.of()));
        assertThrows(IllegalArgumentException.class, () -> matcher.match("[a-", List.of()));
    }
}
