package com.sorbonne.backend.book;

public record BookResponse(
        Long id,
        Integer gutenbergId,
        String title,
        String author,
        String language,
        Integer wordCount,
        BookStatus status) {

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getGutenbergId(),
                book.getTitle(),
                book.getAuthor(),
                book.getLanguage(),
                book.getWordCount(),
                book.getStatus());
    }
}
