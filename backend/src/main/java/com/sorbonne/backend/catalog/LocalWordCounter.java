package com.sorbonne.backend.catalog;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counts Unicode letter words, without relying on filenames or precomputed metadata. */
@Component
public final class LocalWordCounter {
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’\\-]\\p{L}+)*");

    public int count(String text) {
        Matcher matcher = WORD.matcher(text);
        int result = 0;
        while (matcher.find()) result++;
        return result;
    }
}
