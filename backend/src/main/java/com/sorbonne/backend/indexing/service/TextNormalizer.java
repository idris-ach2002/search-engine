package com.sorbonne.backend.indexing.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces the canonical search vocabulary used by the inverted index.
 *
 * <p>The output intentionally stays inside the ASCII alphabet so it remains
 * compatible with the Project 1 regex/search core. Apostrophes and hyphens
 * are kept only when they occur inside a word.</p>
 */
@Component
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN = Pattern.compile("[a-z]+(?:['-][a-z]+)*");

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String canonical = canonicalize(text);
        Matcher matcher = TOKEN.matcher(canonical);
        List<String> tokens = new ArrayList<>();

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return List.copyOf(tokens);
    }

    String canonicalize(String text) {
        String punctuationNormalized = text
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u02BC', '\'')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace("ß", "ss")
                .replace("Æ", "AE")
                .replace("æ", "ae")
                .replace("Œ", "OE")
                .replace("œ", "oe")
                .replace("Ø", "O")
                .replace("ø", "o")
                .replace("Ł", "L")
                .replace("ł", "l");

        String decomposed = Normalizer.normalize(punctuationNormalized, Normalizer.Form.NFKD);
        return COMBINING_MARKS.matcher(decomposed)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}
