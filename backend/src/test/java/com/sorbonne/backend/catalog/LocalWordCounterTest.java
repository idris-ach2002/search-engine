package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalWordCounterTest {
    @Test
    void countsUnicodeWordsAndNotNumbers() {
        assertEquals(4, new LocalWordCounter().count("L'enfant sait écrire 123 maintenant"));
    }
}
