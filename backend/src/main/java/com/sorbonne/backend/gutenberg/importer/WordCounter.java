package com.sorbonne.backend.gutenberg.importer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WordCounter {

    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’\\-]\\p{L}+)*");

    public int count(String text) {
        Matcher matcher = WORD.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
