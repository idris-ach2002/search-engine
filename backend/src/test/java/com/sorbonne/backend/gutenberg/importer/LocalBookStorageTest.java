package com.sorbonne.backend.gutenberg.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sorbonne.backend.config.StorageProperties;
import com.sorbonne.backend.gutenberg.importer.LocalBookStorage.StoredBook;

class LocalBookStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPortableKeyInsteadOfAbsolutePath() throws IOException {
        LocalBookStorage storage = new LocalBookStorage(
                new StorageProperties(tempDir.toString()));

        StoredBook stored = storage.save(10084, "hello Gutenberg");

        assertEquals("10084.txt", stored.storageKey());
        assertFalse(Path.of(stored.storageKey()).isAbsolute());
        assertTrue(storage.resolve(stored.storageKey()).startsWith(tempDir.toAbsolutePath().normalize()));
        assertEquals("hello Gutenberg", storage.read(stored.storageKey()));
    }

    @Test
    void rejectsAbsoluteAndTraversalKeys() throws IOException {
        LocalBookStorage storage = new LocalBookStorage(
                new StorageProperties(tempDir.toString()));

        assertThrows(IOException.class, () -> storage.resolve(tempDir.resolve("outside.txt").toString()));
        assertThrows(IOException.class, () -> storage.resolve("../outside.txt"));
    }
}
