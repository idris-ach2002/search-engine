package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalBookLanguageDetectorTest {
    @Test
    void recognizesIsoCodesAndLanguageNamesBeyondOriginalFiveLanguages() {
        assertEquals("ja", LocalBookLanguageDetector.languageCode("Japanese"));
        assertEquals("nl", LocalBookLanguageDetector.languageCode("Dutch"));
        assertEquals("el", LocalBookLanguageDetector.languageCode("Greek"));
        assertEquals("pt", LocalBookLanguageDetector.languageCode("Portuguese"));
        assertEquals("de", LocalBookLanguageDetector.languageCode("deu"));
        assertEquals("fr", LocalBookLanguageDetector.languageCode("français"));
        assertNull(LocalBookLanguageDetector.languageCode("unknown"));
    }

    @Test
    void recognizesNonLatinScriptsWithoutMistakingCyrillicForRussian() {
        assertEquals("ja", LocalBookLanguageDetector.detect("これは日本語の本です。物語がここから始まります。".repeat(30)));
        assertEquals("el", LocalBookLanguageDetector.detect("Η ιστορία του ανθρώπου και της ζωής είναι μεγάλη. ".repeat(30)));
        assertEquals("zh", LocalBookLanguageDetector.detect("這是一個關於人類歷史的故事。".repeat(30)));
        assertNull(LocalBookLanguageDetector.detect("абвгд ".repeat(100)));
    }

    @Test
    void detectsDutchAndGermanAndAbstainsForSparseOrAmbiguousText() {
        assertEquals("nl", LocalBookLanguageDetector.detect(
                "Het was een dag waarop hij met haar naar de stad ging en zij niet wist wat er zou gebeuren. ".repeat(20)));
        assertEquals("de", LocalBookLanguageDetector.detect(
                "Der Mann war in dem Haus und er hatte eine Geschichte die er nicht mit ihr teilen wollte. ".repeat(20)));
        assertNull(LocalBookLanguageDetector.detect("Hello world"));
        assertNull(LocalBookLanguageDetector.detect("the ".repeat(1000)));
        assertNull(LocalBookLanguageDetector.detect("og det at en et som på den til er var med for han hun ikke om men jeg så fra skulle dette ".repeat(20)));
    }
}
