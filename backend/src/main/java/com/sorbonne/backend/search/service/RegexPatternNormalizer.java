package com.sorbonne.backend.search.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class RegexPatternNormalizer {

    private static final int MAX_PATTERN_LENGTH = 256;

    /**
     * Removes accents while keeping Java regex syntax and letter case intact.
     * The compiled Pattern is configured to match without case sensitivity.
     */
    public String normalize(String regex) {
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("Regex must not be blank");
        }
        if (regex.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Regex is too long (maximum 256 characters)");
        }

        String decomposed = Normalizer.normalize(regex, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char character = decomposed.charAt(i);
            int type = Character.getType(character);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            result.append(character);
        }

        // Preserve the spelling of Java regex operators, flags, character
        // classes and Unicode properties. Matching ignores case via Pattern.
        String normalized = result.toString();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Regex must contain a searchable pattern");
        }
        return normalized;
    }
}
