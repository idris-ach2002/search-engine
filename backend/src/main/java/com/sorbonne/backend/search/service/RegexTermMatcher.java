package com.sorbonne.backend.search.service;

import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class RegexTermMatcher {

    private final RegexPatternNormalizer patternNormalizer;

    public RegexTermMatcher(RegexPatternNormalizer patternNormalizer) {
        this.patternNormalizer = patternNormalizer;
    }

    /**
     * Compiles the Java regex once per request and tests the indexed vocabulary.
     * Matcher.find() preserves the former substring-search semantics.
     * No custom NFA/DFA or exponential subset construction is used.
     */
    public MatchResult match(String regex, List<IndexedTerm> terms) {
        String normalizedRegex = patternNormalizer.normalize(regex);
        final Pattern compiled;
        try {
            compiled = Pattern.compile(normalizedRegex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Invalid regex: " + exception.getDescription(), exception);
        }

        List<IndexedTerm> matches = terms.stream()
                .filter(term -> compiled.matcher(term.value()).find())
                .toList();

        return new MatchResult(normalizedRegex, matches);
    }

    public record MatchResult(String normalizedRegex, List<IndexedTerm> terms) {
    }
}
