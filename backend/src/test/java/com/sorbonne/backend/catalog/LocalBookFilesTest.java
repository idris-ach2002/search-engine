package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalBookFilesTest {
    @TempDir Path dir;

    @Test
    void usesRelativeStorageKeyAndChecksHash() throws Exception {
        Path root = Files.createDirectories(dir.resolve("books"));
        Files.writeString(root.resolve("anonymous.txt"), "Book content");
        LocalBookFiles files = new LocalBookFiles(root.toString(), 100_000);
        var stored = files.read("anonymous.txt");
        assertEquals("Book content", stored.text());
        assertEquals(12, stored.contentBytes());
        assertEquals(64, stored.sha256().length());
    }

    @Test
    void refusesPathTraversalAndAbsolutePaths() throws Exception {
        Path root = Files.createDirectories(dir.resolve("books"));
        LocalBookFiles files = new LocalBookFiles(root.toString(), 100_000);
        assertThrows(IllegalArgumentException.class, () -> files.resolve("../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> files.resolve("/tmp/outside.txt"));
    }
}
