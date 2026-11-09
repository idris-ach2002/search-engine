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
            if (known != null) {
                updated.add(known);
                reused++;
            } else {
                updated.add(metadata.infer(key, files.resolve(key)));
                inferred++;
            }
        }
        boolean changed = csv.writeIfChanged(updated);
        return new GenerationResult(updated.size(), reused, inferred, changed);
    }

    public record GenerationResult(int localBooks, int preservedMetadata, int inferredMetadata,
                                   boolean catalogUpdated) {}
}
