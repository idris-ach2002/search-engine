package com.sorbonne.backend.gutenberg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sorbonne.backend.gutenberg.importer.WordCounter;

class WordCounterTest {

    private final WordCounter counter = new WordCounter();

    @Test
    void countsUnicodeWordsAndApostrophes() {
        assertEquals(5, counter.count("L'amour d'Idris est très grand."));
    }
}
