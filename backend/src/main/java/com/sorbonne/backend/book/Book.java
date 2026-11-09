package com.sorbonne.backend.book;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String author;

    @Column(length = 32)
    private String language;

    @Column(name = "word_count")
    private Integer wordCount;

    /**
     * Portable key relative to the configured storage backend.
     * Example: "opaque-file-hash.txt"; no metadata is inferred from the name.
     *
     * Never store an absolute filesystem path here: the storage root belongs
     * to application configuration, not to persisted business data.
     */
    @Column(name = "storage_key", nullable = false, unique = true, columnDefinition = "text")
    private String storageKey;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "content_bytes")
    private Long contentBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Book() {
    }

    public Book(String storageKey, String title, String author, String language, String sourceUrl) {
        this.storageKey = storageKey;
        this.title = title;
        this.author = author;
        this.language = language;
        this.sourceUrl = sourceUrl;
        this.status = BookStatus.READY;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateMetadata(String title, String author, String language, String sourceUrl) {
        this.title = title;
        this.author = author;
        this.language = language;
        this.sourceUrl = sourceUrl;
    }

    public void markReady(int wordCount, String storageKey, String sha256, long contentBytes) {
        this.wordCount = wordCount;
        this.storageKey = storageKey;
        this.contentSha256 = sha256;
        this.contentBytes = contentBytes;
        this.status = BookStatus.READY;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public Long getContentBytes() {
        return contentBytes;
    }

    public BookStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
