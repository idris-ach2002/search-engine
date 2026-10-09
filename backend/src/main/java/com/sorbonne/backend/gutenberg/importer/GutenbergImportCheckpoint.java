package com.sorbonne.backend.gutenberg.importer;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "gutenberg_import_checkpoint")
public class GutenbergImportCheckpoint {

    @Id
    @Column(length = 64)
    private String source;

    @Column(name = "page_url", nullable = false, columnDefinition = "text")
    private String pageUrl;

    @Column(name = "link_index", nullable = false)
    private int linkIndex;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GutenbergImportCheckpoint() {
    }

    public GutenbergImportCheckpoint(String source, String pageUrl) {
        this.source = source;
        this.pageUrl = pageUrl;
        this.linkIndex = 0;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getSource() {
        return source;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public int getLinkIndex() {
        return linkIndex;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void moveWithinPage(int nextLinkIndex) {
        this.linkIndex = nextLinkIndex;
    }

    public void moveToNextPage(String nextPageUrl) {
        this.pageUrl = nextPageUrl;
        this.linkIndex = 0;
    }
}
