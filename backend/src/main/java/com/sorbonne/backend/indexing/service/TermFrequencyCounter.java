package com.sorbonne.backend.indexing.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public final class TermFrequencyCounter {

    private final TextNormalizer textNormalizer;

    public TermFrequencyCounter(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    public Map<String, Integer> count(String text) {
        Map<String, Integer> frequencies = new HashMap<>();
        textNormalizer.forEachToken(text, token -> frequencies.merge(token, 1, Integer::sum));
        return Map.copyOf(frequencies);
    }
}
