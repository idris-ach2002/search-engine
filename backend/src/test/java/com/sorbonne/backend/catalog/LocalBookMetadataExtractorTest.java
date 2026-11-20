package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalBookMetadataExtractorTest {
    @TempDir Path temporary;

    private CatalogCsvReader.Entry extract(String text) throws Exception {
        Path file = temporary.resolve("book_0002.txt");
        Files.writeString(file, text);
        return new LocalBookMetadataExtractor().infer(file.getFileName().toString(), file);
    }

    @Test
    void readsStrippedGutenbergTitlePageAndDetectsLanguage() throws Exception {
        var entry = extract("/\n\nThe War of the Worlds\n\nby H. G. Wells\n\nContents\n\n"
                + "CHAPTER I\n\n" + english());
        assertEquals("The War of the Worlds", entry.title());
        assertEquals("H. G. Wells", entry.author());
        assertEquals("en", entry.language());
        assertNull(entry.sourceUrl()); // A sequential storage key is not a Gutenberg ID.
    }

    @Test
    void supportsFrenchBylineOnSeparateLinesAndIgnoresProductionCredits() throws Exception {
        var entry = extract("Produced by Alice and the Online\nProofreading Team at https://www.pgdp.net\n\n"
                + "_Transcriber's Notes_\n\nTypographical errors have been corrected.\n\n"
                + "LA\n\nFEMME DU DIABLE\n\nPAR\n\nJoseph LAFON-LABATUT\n\nPRÉFACE\n\n"
                + "La femme était dans la maison et elle avait un livre. Il ne savait pas ce que les autres "
                + "avaient dit mais il se demandait pourquoi elle était venue avec ses enfants pour le voir. ".repeat(10));
        assertEquals("LA FEMME DU DIABLE", entry.title());
        assertEquals("Joseph LAFON-LABATUT", entry.author());
        assertEquals("fr", entry.language());
        assertNull(entry.sourceUrl());
    }

    @Test
    void explicitFieldsTakePriorityAndSupportBomCrLfIndentationAndWrappedTitles() throws Exception {
        var entry = extract("\uFEFF  Title: A Long Title\r\n    with a subtitle\r\n"
                + "  Author: The Named Author\r\n  Language: Japanese\r\n"
                + "Source_URL: https://example.org/books/42\r\n\r\n"
                + "*** START OF THE PROJECT GUTENBERG EBOOK OTHER TITLE ***\r\n"
                + "Another Title\r\n\r\nby Somebody Else\r\n\r\n" + english());
        assertEquals("A Long Title with a subtitle", entry.title());
        assertEquals("The Named Author", entry.author());
        assertEquals("ja", entry.language());
        assertEquals("https://example.org/books/42", entry.sourceUrl());
    }

    @Test
    void multilineFieldsStopAtBlankLinesAndNextLabels() throws Exception {
        var entry = extract("Title: First Title\n  Author: Jane Doe\nLanguage: English\n\n"
                + "    This indented paragraph is prose.\n");
        assertEquals("First Title", entry.title());
        assertEquals("Jane Doe", entry.author());
        var blank = extract("Title: First Title\n\n    Not part of the title\n\n");
        assertEquals("First Title", blank.title());
    }

    @Test
    void extractsAllFieldsFromGutenbergHeader() throws Exception {
        var entry = extract("The Project Gutenberg eBook of The War of the Worlds, by H. G. Wells\n\n"
                + "Release date: January 1, 2000 [EBook #36]\nLanguage: English\n\n"
                + "*** START OF THE PROJECT GUTENBERG EBOOK THE WAR OF THE WORLDS ***\n\n" + english());
        assertEquals("The War of the Worlds", entry.title());
        assertEquals("H. G. Wells", entry.author());
        assertEquals("en", entry.language());
        assertEquals("https://www.gutenberg.org/ebooks/36", entry.sourceUrl());
    }

    @Test
    void supportsGutenbergEtextAndCanonicalizesItsBookUrl() throws Exception {
        var entry = extract("The Project Gutenberg Etext of The Orange Fairy Book\nby Andrew Lang\n"
                + "(#29 in our series by Andrew Lang)\n\n"
                + "https://www.gutenberg.org/cache/epub/3027/pg3027.txt\n\nLanguage: English\n");
        assertEquals("The Orange Fairy Book", entry.title());
        assertEquals("Andrew Lang", entry.author());
        assertEquals("https://www.gutenberg.org/ebooks/3027", entry.sourceUrl());
    }

    @Test
    void ignoresMetadataAndBylinesInsideChapters() throws Exception {
        var entry = extract("A Real Title\n\nCHAPTER I\n\n"
                + "Title: The Book They Were Reading\nAuthor: A Character\nLanguage: Japanese\n"
                + "Source: https://example.org/unrelated\nby An Impostor\n\n" + english());
        assertEquals("A Real Title", entry.title());
        assertNull(entry.author());
        assertEquals("en", entry.language());
        assertNull(entry.sourceUrl());
    }

    @Test
    void doesNotInventMetadataForProseOrUnsupportedLanguage() throws Exception {
        var entry = extract("Once upon a time there was a story.");
        assertEquals("book 0002", entry.title());
        assertNull(entry.author());
        assertNull(entry.language());
        assertNull(entry.sourceUrl());
        assertEquals("book 0002", extract("Texte libre").title());
    }

    @Test
    void ignoresGutenbergDiscussionInBodyAndRejectsInvalidSource() throws Exception {
        var entry = extract("A History of Books\n\nSource: javascript:alert(1)\n\nCHAPTER I\n\n"
                + "Project Gutenberg's goal is more about selecting books for readers by a large margin.\n\n"
                + english());
        assertEquals("A History of Books", entry.title());
        assertNull(entry.author());
        assertNull(entry.sourceUrl());
    }

    @Test
    void distinguishesAuthorFromTranslator() throws Exception {
        var entry = extract("Produced by R. S.\n\nUntersuchungen über die radioaktiven Substanzen\n"
                + "    Marie Curie\n    (übersetzt von W. Kaufmann)\n\nEinleitung.\n\n");
        assertEquals("Untersuchungen über die radioaktiven Substanzen", entry.title());
        assertEquals("Marie Curie", entry.author());
    }

    @Test
    void choosesTitleNearAuthorAndRejectsNarrativeByline() throws Exception {
        var entry = extract("This etext was produced from a magazine.\n\nA Sequel to Another Story\n\n"
                + "By way of a mysterious tube, the travellers enter another world.\n\n"
                + "The Fifth-Dimension Tube\n\n_A Complete Novelette_\n\nBy Murray Leinster\n\nCHAPTER I\n\n");
        assertEquals("The Fifth-Dimension Tube", entry.title());
        assertEquals("Murray Leinster", entry.author());
    }

    @Test
    void choosesBookInsteadOfSeriesHeadingAndIgnoresPublisherByline() throws Exception {
        var entry = extract("ALL-STORY WEEKLY\n\nVOL. XC\n\nThe Pirate Woman\n\nby Captain Dingle\n\nContents\n\n");
        assertEquals("The Pirate Woman", entry.title());
        assertEquals("Captain Dingle", entry.author());
        var publisher = extract("A Real Title\n\nPrinted and bound\nby A. Smith Publishing Company\n\nContents\n\n");
        assertEquals("A Real Title", publisher.title());
        assertNull(publisher.author());
    }

    @Test
    void titleDoesNotAbsorbTranslatorWhenNoAuthorIsPresent() throws Exception {
        var entry = extract("THE ROAD\nTO THE SEA\n(translated by Jane Doe)\n\nCHAPTER I\n\n");
        assertEquals("THE ROAD TO THE SEA", entry.title());
        assertNull(entry.author());
    }

    private static String english() {
        return "The people were in the house and she was with her family. He had not seen them before, "
                + "but they would have their books on the table. This was what he had been waiting for. ".repeat(10);
    }
}
