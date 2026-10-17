package com.sorbonne.backend.indexing.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BookIndexingService {

    private static final int MAX_BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbcTemplate;
    private final BookIndexingWorker worker;

    public BookIndexingService(JdbcTemplate jdbcTemplate, BookIndexingWorker worker) {
        this.jdbcTemplate = jdbcTemplate;
        this.worker = worker;
    }

    public synchronized BookIndexingWorker.IndexedBook indexBook(long bookId) {
        return worker.indexBook(bookId);
    }

    /**
     * Indexes READY books that have not been indexed yet. Each book runs in its
     * own transaction through BookIndexingWorker, so one bad file does not roll
     * back successful books from the same batch.
     */
    public synchronized BatchResult indexPending(int maxBooks) {
        int effectiveLimit = validateBatchSize(maxBooks);
        long startedAt = System.nanoTime();

        List<Long> pendingBookIds = jdbcTemplate.queryForList(
                """
                SELECT b.id
                FROM books b
                WHERE b.status = 'READY'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM book_terms bt
                      WHERE bt.book_id = b.id
                  )
                ORDER BY b.id
                LIMIT ?
                """,
                Long.class,
                effectiveLimit
        );

        List<BookIndexingWorker.IndexedBook> indexed = new ArrayList<>();
        List<FailedBook> failed = new ArrayList<>();

        for (Long bookId : pendingBookIds) {
            try {
                indexed.add(worker.indexBook(bookId));
            } catch (RuntimeException exception) {
                failed.add(new FailedBook(bookId, safeMessage(exception)));
            }
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new BatchResult(
                pendingBookIds.size(),
                indexed.size(),
                failed.size(),
                List.copyOf(indexed),
                List.copyOf(failed),
                elapsedMillis
        );
    }

    public IndexingStatus status() {
        long readyBooks = queryCount("SELECT COUNT(*) FROM books WHERE status = 'READY'");
        long indexedBooks = queryCount(
                """
                SELECT COUNT(*)
                FROM books b
                WHERE b.status = 'READY'
                  AND EXISTS (
                      SELECT 1
                      FROM book_terms bt
                      WHERE bt.book_id = b.id
                  )
                """
        );
        long terms = queryCount("SELECT COUNT(*) FROM terms");
        long bookTerms = queryCount("SELECT COUNT(*) FROM book_terms");

        return new IndexingStatus(
                readyBooks,
                indexedBooks,
                Math.max(0, readyBooks - indexedBooks),
                terms,
                bookTerms
        );
    }

    private int validateBatchSize(int maxBooks) {
        if (maxBooks < 1 || maxBooks > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "maxBooks must be between 1 and " + MAX_BATCH_SIZE
            );
        }
        return maxBooks;
    }

    private long queryCount(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    public record FailedBook(long bookId, String error) {
    }

    public record BatchResult(
            int attempted,
            int indexed,
            int failed,
            List<BookIndexingWorker.IndexedBook> books,
            List<FailedBook> failures,
            long elapsedMillis
    ) {
    }

    public record IndexingStatus(
            long readyBooks,
            long indexedBooks,
            long unindexedBooks,
            long terms,
            long bookTerms
    ) {
    }
}
