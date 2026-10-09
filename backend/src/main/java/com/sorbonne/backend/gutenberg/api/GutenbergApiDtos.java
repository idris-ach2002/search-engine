package com.sorbonne.backend.gutenberg.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class GutenbergApiDtos {

    private GutenbergApiDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BooksPage(
            String next,
            String previous,
            List<BookSummary> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookSummary(
            Integer id,
            String title,
            @JsonProperty("alternative_title") String alternativeTitle,
            List<Author> authors,
            List<String> subjects,
            List<String> bookshelves,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("download_count") Long downloadCount,
            String issued,
            @JsonProperty("reading_ease_score") String readingEaseScore,
            @JsonProperty("cover_image") String coverImage,
            String language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(
            Integer id,
            String name,
            @JsonProperty("birth_year") Integer birthYear,
            @JsonProperty("death_year") Integer deathYear) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextResponse(
            @JsonProperty("book_id") Integer bookId,
            String title,
            @JsonProperty("alternative_title") String alternativeTitle,
            @JsonProperty("cleaning_mode") String cleaningMode,
            String text,
            TextMetadata metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextMetadata(
            @JsonProperty("original_length") Long originalLength,
            @JsonProperty("cleaned_length") Long cleanedLength,
            @JsonProperty("source_format") String sourceFormat,
            @JsonProperty("source_url") String sourceUrl) {
    }
}
