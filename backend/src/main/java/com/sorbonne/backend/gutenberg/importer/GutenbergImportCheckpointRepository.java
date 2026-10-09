package com.sorbonne.backend.gutenberg.importer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GutenbergImportCheckpointRepository
        extends JpaRepository<GutenbergImportCheckpoint, String> {
}
