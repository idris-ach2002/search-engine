package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCsvReaderTest {
    @TempDir Path dir;

    @Test
    void readsIndependentTitleAndQuotedCommas() throws Exception {
        Path csv = dir.resolve("catalog.csv");
        Files.writeString(csv, "storage_key,title,author,language,source_url\n"
                + "any/path/opaque.txt,\"This, not the file name\",\"Author \"\"A\"\"\",en,\n");
        var rows = new CatalogCsvReader(csv.toString()).read();
        assertEquals(1, rows.size());
        assertEquals("This, not the file name", rows.getFirst().title());
        assertEquals("Author \"A\"", rows.getFirst().author());
        assertEquals("any/path/opaque.txt", rows.getFirst().storageKey());
    }

    @Test
    void rejectsDuplicateStorageKeys() throws Exception {
        Path csv = dir.resolve("catalog.csv");
        Files.writeString(csv, "storage_key,title,author,language,source_url\n"
                + "same.txt,One,,,\nsame.txt,Two,,,\n");
        assertThrows(IllegalArgumentException.class, () -> new CatalogCsvReader(csv.toString()).read());
    }
}
