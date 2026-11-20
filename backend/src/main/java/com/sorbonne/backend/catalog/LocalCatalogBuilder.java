package com.sorbonne.backend.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Materializes catalog.csv from data/books, preserving manually supplied and downloaded metadata. */
@Component
public final class LocalCatalogBuilder {
    private final CatalogCsvReader csv;
    private final LocalBookFiles files;
    private final LocalBookMetadataExtractor metadata;

    public LocalCatalogBuilder(CatalogCsvReader csv, LocalBookFiles files, LocalBookMetadataExtractor metadata) {
        this.csv = csv;
        this.files = files;
        this.metadata = metadata;
    }

    public synchronized GenerationResult generate() {
        List<String> storageKeys = files.listStorageKeys();
        if (storageKeys.isEmpty()) {
            throw new IllegalArgumentException("No .txt book found in data/books; catalog was not modified");
        }
        if (storageKeys.size() > 10000) {
            throw new IllegalArgumentException("Maximum 10000 local books per catalog");
        }
        Map<String, CatalogCsvReader.Entry> previous = new HashMap<>();
        for (CatalogCsvReader.Entry entry : csv.readIfExists()) {
            previous.put(entry.storageKey(), entry);
        }
        int reused = 0;
        int inferred = 0;
        List<CatalogCsvReader.Entry> updated = new ArrayList<>(storageKeys.size());
        for (String key : storageKeys) {
            CatalogCsvReader.Entry known = previous.get(key);
            if (known != null && !needsEnrichment(known)) {
                updated.add(known);
                reused++;
            } else {
                CatalogCsvReader.Entry extracted = metadata.infer(key, files.resolve(key));
                CatalogCsvReader.Entry merged = known == null ? extracted : merge(known, extracted);
                updated.add(merged);
                if (merged.equals(known)) reused++;
                else inferred++;
            }
        }
        boolean changed = csv.writeIfChanged(updated);
        return new GenerationResult(updated.size(), reused, inferred, changed);
    }

    private static boolean needsEnrichment(CatalogCsvReader.Entry entry) {
        return entry.title().equals(LocalBookMetadataExtractor.filenameLabel(entry.storageKey()))
                || entry.author() == null || entry.language() == null || entry.sourceUrl() == null;
    }

    private static CatalogCsvReader.Entry merge(CatalogCsvReader.Entry known, CatalogCsvReader.Entry extracted) {
        boolean fallbackTitle = known.title().equals(LocalBookMetadataExtractor.filenameLabel(known.storageKey()));
        return new CatalogCsvReader.Entry(known.storageKey(), fallbackTitle ? extracted.title() : known.title(),
                known.author() == null ? extracted.author() : known.author(),
                known.language() == null ? extracted.language() : known.language(),
                known.sourceUrl() == null ? extracted.sourceUrl() : known.sourceUrl());
    }

    public record GenerationResult(int localBooks, int preservedMetadata, int inferredMetadata,
                                   boolean catalogUpdated) {}
}
