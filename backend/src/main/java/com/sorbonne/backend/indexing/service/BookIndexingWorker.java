package com.sorbonne.backend.indexing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class BookIndexingWorker {

    private static final int QUERY_CHUNK_SIZE = 750;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TermFrequencyCounter frequencyCounter;
    private final Path booksRoot;

    public BookIndexingWorker(
            JdbcTemplate jdbcTemplate,
            TermFrequencyCounter frequencyCounter,
            @Value("${app.storage.books-root:./data/books}") String booksRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.frequencyCounter = frequencyCounter;
        this.booksRoot = Path.of(booksRoot).toAbsolutePath().normalize();
    }

    /**
     * Rebuilds one book's inverted-index rows atomically.
     */
    @Transactional
    public IndexedBook indexBook(long bookId) {
        long startedAt = System.nanoTime();
        BookRow book = findBook(bookId);

        if (!"READY".equals(book.status())) {
            throw new IllegalArgumentException("Book " + bookId + " is not READY (status=" + book.status() + ")");
        }

        Path textPath = resolveStorageKey(book.storageKey());
        String text = readText(textPath);
        Map<String, Integer> frequencies = frequencyCounter.count(text);

        if (frequencies.isEmpty()) {
            throw new IllegalStateException("Book " + bookId + " produced no indexable terms");
        }

        Set<Long> previouslyLinkedTermIds = new HashSet<>(
                jdbcTemplate.queryForList(
                        "SELECT term_id FROM book_terms WHERE book_id = ?",
                        Long.class,
                        bookId
                )
        );

        // Reindexing is idempotent: remove the previous posting list first.
        jdbcTemplate.update("DELETE FROM book_terms WHERE book_id = ?", bookId);

        List<String> terms = frequencies.keySet().stream()
                .sorted()
                .toList();

        insertMissingTerms(terms);
        Map<String, Long> termIds = loadTermIds(terms);

        if (termIds.size() != terms.size()) {
            throw new IllegalStateException(
                    "Could not resolve every term id for book " + bookId
                            + " (expected=" + terms.size() + ", actual=" + termIds.size() + ")"
            );
        }

        insertBookTerms(bookId, frequencies, termIds);

        Set<Long> affectedTermIds = new HashSet<>(previouslyLinkedTermIds);
        affectedTermIds.addAll(termIds.values());
        refreshDocumentFrequencies(affectedTermIds);

        // A term can become orphaned when an already-indexed book is reindexed.
        jdbcTemplate.update("DELETE FROM terms WHERE document_frequency = 0");

        long totalTokens = frequencies.values().stream().mapToLong(Integer::longValue).sum();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        return new IndexedBook(
                book.id(),
                book.gutenbergId(),
                book.storageKey(),
                frequencies.size(),
                totalTokens,
                elapsedMillis
        );
    }

    private BookRow findBook(long bookId) {
        List<BookRow> rows = jdbcTemplate.query(
                """
                SELECT id, gutenberg_id, storage_key, status
                FROM books
                WHERE id = ?
                """,
                (rs, rowNum) -> new BookRow(
                        rs.getLong("id"),
                        rs.getInt("gutenberg_id"),
                        rs.getString("storage_key"),
                        rs.getString("status")
                ),
                bookId
        );

        if (rows.isEmpty()) {
            throw new NoSuchElementException("Book " + bookId + " was not found");
        }
        return rows.getFirst();
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException("Book has no storage_key");
        }

        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) {
            throw new IllegalStateException("storage_key must be relative: " + storageKey);
        }

        Path resolved = booksRoot.resolve(relative).normalize();
        if (!resolved.startsWith(booksRoot)) {
            throw new IllegalStateException("storage_key escapes books root: " + storageKey);
        }
        return resolved;
    }

    private String readText(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Stored book file does not exist: " + path);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read stored book file: " + path, exception);
        }
    }

    private void insertMissingTerms(List<String> terms) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO terms(value, document_frequency) VALUES (?, 0) ON CONFLICT (value) DO NOTHING",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, terms.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return terms.size();
                    }
                }
        );
    }

    private Map<String, Long> loadTermIds(List<String> terms) {
        Map<String, Long> ids = new HashMap<>();

        for (List<String> chunk : chunks(terms)) {
            MapSqlParameterSource parameters = new MapSqlParameterSource("values", chunk);
            namedJdbcTemplate.query(
                    "SELECT id, value FROM terms WHERE value IN (:values)",
                    parameters,
                    rs -> {
                        ids.put(rs.getString("value"), rs.getLong("id"));
                    }
            );
        }
        return ids;
    }

    private void insertBookTerms(
            long bookId,
            Map<String, Integer> frequencies,
            Map<String, Long> termIds
    ) {
        List<Map.Entry<String, Integer>> postings = frequencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book_terms(book_id, term_id, occurrences)
                VALUES (?, ?, ?)
                ON CONFLICT (book_id, term_id)
                DO UPDATE SET occurrences = EXCLUDED.occurrences
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map.Entry<String, Integer> posting = postings.get(i);
                        ps.setLong(1, bookId);
                        ps.setLong(2, termIds.get(posting.getKey()));
                        ps.setInt(3, posting.getValue());
                    }

                    @Override
                    public int getBatchSize() {
                        return postings.size();
                    }
                }
        );
    }

    private void refreshDocumentFrequencies(Set<Long> termIds) {
        if (termIds.isEmpty()) {
            return;
        }

        List<Long> sortedIds = termIds.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        for (List<Long> chunk : chunks(sortedIds)) {
            MapSqlParameterSource parameters = new MapSqlParameterSource("ids", chunk);
            namedJdbcTemplate.update(
                    """
                    UPDATE terms t
                    SET document_frequency = (
                        SELECT COUNT(*)::integer
                        FROM book_terms bt
                        WHERE bt.term_id = t.id
                    )
                    WHERE t.id IN (:ids)
                    """,
                    parameters
            );
        }
    }

    private static <T> List<List<T>> chunks(List<T> values) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += QUERY_CHUNK_SIZE) {
            int end = Math.min(values.size(), start + QUERY_CHUNK_SIZE);
            chunks.add(values.subList(start, end));
        }
        return chunks;
    }

    private record BookRow(long id, int gutenbergId, String storageKey, String status) {
    }

    public record IndexedBook(
            long bookId,
            int gutenbergId,
            String storageKey,
            int uniqueTerms,
            long totalTokens,
            long elapsedMillis
    ) {
    }
}
