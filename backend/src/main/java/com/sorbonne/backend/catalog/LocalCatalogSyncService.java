package com.sorbonne.backend.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Synchronizes books on disk, with an automatically generated, metadata-preserving catalog. */
@Service
public class LocalCatalogSyncService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final CatalogCsvReader catalog;
    private final LocalCatalogBuilder builder;
    private final LocalBookFiles files;
    private final LocalWordCounter wordCounter;
    private final int minimumWordCount;

    public LocalCatalogSyncService(JdbcTemplate jdbc, CatalogCsvReader catalog, LocalCatalogBuilder builder,
                                   LocalBookFiles files, LocalWordCounter wordCounter,
                                   @Value("${app.catalog.minimum-word-count:10000}") int minimumWordCount) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.catalog = catalog;
        this.builder = builder;
        this.files = files;
        this.wordCounter = wordCounter;
        if (minimumWordCount < 10000) throw new IllegalArgumentException("minimum-word-count must be >= 10000");
        this.minimumWordCount = minimumWordCount;
    }

    @Transactional
    public synchronized SyncResult sync(boolean prune) {
        long start = System.nanoTime();
        // Reconcile data/books against catalog.csv before each import, including a missing CSV.
        builder.generate();
        List<CatalogCsvReader.Entry> entries = catalog.read();
        if (entries.isEmpty()) throw new IllegalArgumentException("The local catalog is empty; refusing to sync/prune");
        if (entries.size() > 10000) throw new IllegalArgumentException("Maximum 10000 catalog entries per sync");

        Map<String, Existing> existing = new HashMap<>();
        jdbc.query("SELECT id, storage_key, title, author, language, source_url, content_sha256, word_count, content_bytes, status FROM books",
                rs -> {
                    Existing book = new Existing(rs.getLong("id"),rs.getString("title"),rs.getString("author"),
                            rs.getString("language"),rs.getString("source_url"),rs.getString("content_sha256"),
                            rs.getInt("word_count"),rs.getLong("content_bytes"),rs.getString("status"));
                    existing.put(rs.getString("storage_key"),book);
                });
        int inserted = 0, updated = 0, unchanged = 0, deleted = 0;
        boolean graphDirty = false;
        for (CatalogCsvReader.Entry entry : entries) {
            LocalBookFiles.StoredText stored = files.read(entry.storageKey());
            int words = wordCounter.count(stored.text());
            if (words < minimumWordCount) {
                throw new IllegalArgumentException("Book " + entry.storageKey() + " has " + words
                        + " words, minimum is " + minimumWordCount);
            }
            Existing previous = existing.remove(entry.storageKey());
            if (previous == null) {
                jdbc.update("""
                        INSERT INTO books(storage_key,title,author,language,source_url,word_count,content_sha256,content_bytes,status,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,'READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """, entry.storageKey(), entry.title(), entry.author(), entry.language(),
                        entry.sourceUrl(), words, stored.sha256(), stored.contentBytes());
                inserted++;
                graphDirty = true;
            } else {
                boolean changedBody = !Objects.equals(previous.sha256(), stored.sha256())
                        || !"READY".equals(previous.status());
                boolean changedMetadata = !Objects.equals(previous.title(),entry.title())
                        || !Objects.equals(previous.author(),entry.author())
                        || !Objects.equals(previous.language(),entry.language())
                        || !Objects.equals(previous.sourceUrl(),entry.sourceUrl())
                        || previous.words()!=words || previous.bytes()!=stored.contentBytes();
                if (changedBody || changedMetadata) {
                    jdbc.update("""
                            UPDATE books SET title=?,author=?,language=?,source_url=?,word_count=?,
                            content_sha256=?,content_bytes=?,status='READY',updated_at=NOW() WHERE id=?
                            """, entry.title(), entry.author(), entry.language(), entry.sourceUrl(),
                            words, stored.sha256(), stored.contentBytes(), previous.id());
                    if (changedBody) {
                        jdbc.update("DELETE FROM book_terms WHERE book_id=?", previous.id());
                        graphDirty = true;
                    }
                    updated++;
                } else unchanged++;
            }
        }
        if (prune && !existing.isEmpty()) {
            deleted = named.update("DELETE FROM books WHERE storage_key IN (:keys)",
                    new MapSqlParameterSource("keys", existing.keySet()));
            graphDirty |= deleted > 0;
        }
        if (graphDirty) {
            // Recompute document frequency after cascading posting deletions.
            jdbc.update("UPDATE terms SET document_frequency=0");
            jdbc.update("""
                    UPDATE terms t SET document_frequency=counts.c
                    FROM (SELECT term_id, COUNT(*)::integer c FROM book_terms GROUP BY term_id) counts
                    WHERE counts.term_id=t.id
                    """);
            jdbc.update("DELETE FROM terms WHERE document_frequency=0");
            jdbc.update("DELETE FROM book_similarities");
            jdbc.update("DELETE FROM book_pageranks");
            jdbc.update("DELETE FROM pagerank_state");
        }
        return new SyncResult(entries.size(),inserted,updated,unchanged,deleted,graphDirty,
                (System.nanoTime()-start)/1_000_000L);
    }

    @Transactional(readOnly=true)
    public CatalogStatus status() {
        Long ready = jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE status='READY'",Long.class);
        Long indexed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM books b WHERE b.status='READY'
                AND EXISTS (SELECT 1 FROM book_terms bt WHERE bt.book_id=b.id)
                """,Long.class);
        return new CatalogStatus(files.listStorageKeys().size(), catalog.readIfExists().size(),
                ready==null?0:ready, indexed==null?0:indexed, minimumWordCount);
    }

    private record Existing(long id,String title,String author,String language,String sourceUrl,
                            String sha256,int words,long bytes,String status) {}
    public record SyncResult(int catalogBooks,int inserted,int updated,int unchanged,int deleted,
                             boolean indexAndGraphInvalidated,long elapsedMillis) {}
    public record CatalogStatus(int localFiles,int catalogBooks,long readyBooks,long indexedBooks,
                                int minimumWordCount) {}
}
