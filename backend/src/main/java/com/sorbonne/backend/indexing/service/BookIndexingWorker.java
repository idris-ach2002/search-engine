package com.sorbonne.backend.indexing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sorbonne.backend.catalog.LocalBookFiles;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class BookIndexingWorker {

    private final JdbcTemplate jdbcTemplate;
    private final TermFrequencyCounter frequencyCounter;
    private final LocalBookFiles localFiles;

    public BookIndexingWorker(
            JdbcTemplate jdbcTemplate,
            TermFrequencyCounter frequencyCounter,
            LocalBookFiles localFiles
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.frequencyCounter = frequencyCounter;
        this.localFiles = localFiles;
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

        long readingStartedAt = System.nanoTime();
        String text = localFiles.read(book.storageKey()).text();
        long readAt = System.nanoTime();
        Map<String, Integer> frequencies = frequencyCounter.count(text);
        long tokenizedAt = System.nanoTime();

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

        // For this book, each distinct term contributes exactly one to DF.
        // Only insertions/removals of a posting change DF (not its occurrences).
        Set<Long> addedTermIds = new HashSet<>(termIds.values());
        addedTermIds.removeAll(previouslyLinkedTermIds);
        Set<Long> removedTermIds = new HashSet<>(previouslyLinkedTermIds);
        removedTermIds.removeAll(termIds.values());
        adjustDocumentFrequency(addedTermIds, 1);
        adjustDocumentFrequency(removedTermIds, -1);

        // Delete only the removed terms that no longer belong to any book.
        if (!removedTermIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM terms WHERE document_frequency = 0 AND id = ANY (?::bigint[])",
                    ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", removedTermIds.toArray(Long[]::new))));
        }

        // The index changed: derived Jaccard/PageRank data must be rebuilt.
        jdbcTemplate.update("DELETE FROM book_similarities");
        jdbcTemplate.update("DELETE FROM book_pageranks");
        jdbcTemplate.update("DELETE FROM pagerank_state");

        long totalTokens = frequencies.values().stream().mapToLong(Integer::longValue).sum();
        long finishedAt = System.nanoTime();
        long elapsedMillis = (finishedAt - startedAt) / 1_000_000L;

        return new IndexedBook(
                book.id(),
                book.storageKey(),
                frequencies.size(),
                totalTokens,
                elapsedMillis,
                (readAt - readingStartedAt) / 1_000_000L,
                (tokenizedAt - readAt) / 1_000_000L,
                (finishedAt - tokenizedAt) / 1_000_000L
        );
    }

    private BookRow findBook(long bookId) {
        List<BookRow> rows = jdbcTemplate.query(
                """
                SELECT id, storage_key, status
                FROM books
                WHERE id = ?
                """,
                (rs, rowNum) -> new BookRow(
                        rs.getLong("id"),
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

    /** One PostgreSQL statement rather than one INSERT round-trip per term. */
    private void insertMissingTerms(List<String> terms) {
        jdbcTemplate.update(
                "INSERT INTO terms(value, document_frequency) "
                        + "SELECT data.value, 0 FROM unnest(?::text[]) AS data(value) "
                        + "WHERE NOT EXISTS (SELECT 1 FROM terms t WHERE t.value = data.value) "
                        + "ON CONFLICT (value) DO NOTHING",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", terms.toArray(String[]::new)))
        );
    }

    private Map<String, Long> loadTermIds(List<String> terms) {
        Map<String, Long> ids = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, value FROM terms WHERE value = ANY (?::text[])",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", terms.toArray(String[]::new))),
                rs -> { ids.put(rs.getString("value"), rs.getLong("id")); }
        );
        return ids;
    }

    /** Insert all postings using two PG arrays instead of N prepared INSERTs. */
    private void insertBookTerms(long bookId, Map<String, Integer> frequencies, Map<String, Long> termIds) {
        List<Map.Entry<String, Integer>> postings = frequencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        Long[] ids = new Long[postings.size()];
        Integer[] counts = new Integer[postings.size()];
        for (int i = 0; i < postings.size(); i++) {
            var entry = postings.get(i);
            ids[i] = termIds.get(entry.getKey());
            counts[i] = entry.getValue();
        }
        jdbcTemplate.update(
                """
                INSERT INTO book_terms(book_id, term_id, occurrences)
                SELECT ?, data.term_id, data.occurrences
                FROM unnest(?::bigint[], ?::integer[]) AS data(term_id, occurrences)
                WHERE true
                ON CONFLICT (book_id, term_id)
                DO UPDATE SET occurrences = EXCLUDED.occurrences
                """,
                ps -> {
                    ps.setLong(1, bookId);
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", ids));
                    ps.setArray(3, ps.getConnection().createArrayOf("integer", counts));
                }
        );
    }

    private void adjustDocumentFrequency(Set<Long> ids, int delta) {
        if (ids.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE terms SET document_frequency = document_frequency + ? WHERE id = ANY (?::bigint[])",
                ps -> {
                    ps.setInt(1, delta);
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", ids.toArray(Long[]::new)));
                }
        );
    }

    private record BookRow(long id, String storageKey, String status) {
    }

    public record IndexedBook(
            long bookId,
            String storageKey,
            int uniqueTerms,
            long totalTokens,
            long elapsedMillis,
            long fileReadMillis,
            long tokenizationMillis,
            long databaseMillis
    ) {
    }
}
