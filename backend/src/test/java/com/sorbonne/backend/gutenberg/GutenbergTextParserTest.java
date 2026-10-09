package com.sorbonne.backend.gutenberg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.sorbonne.backend.gutenberg.importer.GutenbergTextParser;

class GutenbergTextParserTest {

    private final GutenbergTextParser parser = new GutenbergTextParser();

    @Test
    void extractsMetadataAndRemovesGutenbergBoilerplate() {
        String raw = """
                Title: Example Book
                Author: Jane Doe
                Language: English

                *** START OF THE PROJECT GUTENBERG EBOOK EXAMPLE BOOK ***

                This is the real book content.

                *** END OF THE PROJECT GUTENBERG EBOOK EXAMPLE BOOK ***
                Project Gutenberg license text.
                """;

        var parsed = parser.parse(123, "https://example.test/123.zip", raw);

        assertEquals("Example Book", parsed.title());
        assertEquals("Jane Doe", parsed.author());
        assertEquals("en", parsed.language());
        assertEquals("This is the real book content.", parsed.text());
        assertFalse(parsed.text().contains("license"));
    }
}
