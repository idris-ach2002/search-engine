package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalCatalogBuilderTest {
    @TempDir Path temporary;

    private LocalCatalogBuilder builder(Path books, Path csv) {
        return new LocalCatalogBuilder(new CatalogCsvReader(csv.toString()),
                new LocalBookFiles(books.toString(), 100_000), new LocalBookMetadataExtractor());
    }

    @Test
    void createsCatalogFromFilesAndKeepsTitlesAcrossSyncs() throws Exception {
        Path books = Files.createDirectories(temporary.resolve("books"));
        Path csv = temporary.resolve("catalog.csv");
        Files.writeString(books.resolve("Les_Misérables.txt"), "Texte libre");
        Files.writeString(books.resolve("roman_header.txt"),
                "Title: Le livre, deuxième partie\nAuthor: Écrivain Exemple\nLanguage: French\n\nText body");
        var generator = builder(books, csv);
        var first = generator.generate();
        assertEquals(2, first.localBooks());
        assertEquals(2, first.inferredMetadata());
        assertTrue(first.catalogUpdated());
        var entries = new CatalogCsvReader(csv.toString()).read();
        assertEquals("Les Misérables", entries.getFirst().title());
        assertEquals("Le livre, deuxième partie", entries.get(1).title());
        assertEquals("fr", entries.get(1).language());
        assertEquals("Écrivain Exemple", entries.get(1).author());

        var second = generator.generate();
        assertEquals(2, second.preservedMetadata());
        assertFalse(second.catalogUpdated());

        var reader = new CatalogCsvReader(csv.toString());
        reader.writeIfChanged(java.util.List.of(
                new CatalogCsvReader.Entry("Les_Misérables.txt", "Titre certifié", "Auteur", "fr", "https://example.org"),
                entries.get(1)));
        generator.generate();
        assertEquals("Titre certifié", reader.read().getFirst().title());
        assertEquals("Auteur", reader.read().getFirst().author());
    }

    @Test
    void missingOrOpaqueMetadataIsNotInvented() throws Exception {
        Path books = Files.createDirectories(temporary.resolve("books"));
        Path csv = temporary.resolve("catalog.csv");
        String opaque = "ab".repeat(32) + ".txt";
        Files.writeString(books.resolve(opaque), "Once upon a time there was a story.");
        builder(books, csv).generate();
        assertEquals("Titre inconnu [abababababab]", new CatalogCsvReader(csv.toString()).read().getFirst().title());
        assertNull(new CatalogCsvReader(csv.toString()).read().getFirst().author());
    }

    @Test
    void refusesToEraseCatalogWhenDirectoryIsEmpty() throws Exception {
        Path books = Files.createDirectories(temporary.resolve("books"));
        Path csv = temporary.resolve("catalog.csv");
        String existing = "storage_key,title,author,language,source_url\noriginal.txt,Original,,,\n";
        Files.writeString(csv, existing);
        assertThrows(IllegalArgumentException.class, () -> builder(books, csv).generate());
        assertEquals(existing, Files.readString(csv));
    }

    @Test
    void dropsStaleEntriesFromGeneratedCatalogButNeverDeletesLocalTexts() throws Exception {
        Path books = Files.createDirectories(temporary.resolve("books"));
        Path csv = temporary.resolve("catalog.csv");
        Files.writeString(books.resolve("A.txt"), "a");
        Files.writeString(books.resolve("B.txt"), "b");
        var generator = builder(books, csv);
        generator.generate();
        Files.delete(books.resolve("B.txt"));
        var result = generator.generate();
        assertEquals(1, result.localBooks());
        assertEquals(1, new CatalogCsvReader(csv.toString()).read().size());
        assertTrue(Files.exists(books.resolve("A.txt")));
    }
}
