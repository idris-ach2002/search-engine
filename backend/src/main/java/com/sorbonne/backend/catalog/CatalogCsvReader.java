package com.sorbonne.backend.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** RFC-4180 quotes and commas, one CSV record per line (no embedded newlines). */
@Component
public final class CatalogCsvReader {
    private static final List<String> HEADER = List.of("storage_key", "title", "author", "language", "source_url");
    private final Path manifest;

    public CatalogCsvReader(@Value("${app.catalog.manifest:./data/catalog.csv}") String manifest) {
        this.manifest = Path.of(manifest).toAbsolutePath().normalize();
    }

    public List<Entry> read() {
        final List<String> lines;
        try {
            lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read catalog: " + manifest, failure);
        }
        if (lines.isEmpty() || !parseLine(lines.getFirst().replace("\uFEFF", "")).equals(HEADER)) {
            throw new IllegalArgumentException("catalog.csv must start with header: " + String.join(",", HEADER));
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> fields = parseLine(lines.get(i));
            if (fields.size() != HEADER.size()) {
                throw new IllegalArgumentException("Invalid CSV columns on line " + (i + 1));
            }
            String key = fields.get(0).strip();
            String title = fields.get(1).strip();
            if (key.isEmpty() || title.isEmpty()) {
                throw new IllegalArgumentException("storage_key and title are mandatory on line " + (i + 1));
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicated storage_key in catalog.csv: " + key);
            }
            entries.add(new Entry(key, title, nullIfBlank(fields.get(2)),
                    nullIfBlank(fields.get(3)), nullIfBlank(fields.get(4))));
        }
        return List.copyOf(entries);
    }

    /** A missing catalog is expected for a freshly shared project. */
    public List<Entry> readIfExists() {
        return Files.exists(manifest) ? read() : List.of();
    }

    /** Atomic, idempotent manifest update. Existing metadata is preserved by LocalCatalogBuilder. */
    public boolean writeIfChanged(List<Entry> entries) {
        StringBuilder output = new StringBuilder(String.join(",", HEADER)).append('\n');
        for (Entry entry : entries) {
            output.append(quote(entry.storageKey())).append(',')
                    .append(quote(entry.title())).append(',')
                    .append(quote(entry.author())).append(',')
                    .append(quote(entry.language())).append(',')
                    .append(quote(entry.sourceUrl())).append('\n');
        }
        String content = output.toString();
        Path temporary = null;
        try {
            if (Files.exists(manifest) && Files.readString(manifest, StandardCharsets.UTF_8).equals(content)) {
                return false;
            }
            Path parent = manifest.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".catalog-", ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write catalog: " + manifest, failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* best effort */ }
            }
        }
    }

    private static String quote(String input) {
        if (input == null) return "";
        String value = input.replace('\r', ' ').replace('\n', ' ');
        if (value.contains(",") || value.contains("\"") || value.startsWith(" ") || value.endsWith(" ")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else if (quoted) {
                    quoted = false;
                    closed = true;
                } else if (value.isEmpty() && !closed) {
                    quoted = true;
                } else {
                    throw new IllegalArgumentException("Bad quote in CSV line");
                }
            } else if (c == ',' && !quoted) {
                fields.add(value.toString());
                value.setLength(0);
                closed = false;
            } else {
                if (closed) throw new IllegalArgumentException("Invalid characters after closing CSV quote");
                value.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated CSV quote");
        fields.add(value.toString());
        return List.copyOf(fields);
    }

    private static String nullIfBlank(String value) {
        return value.isBlank() ? null : value.strip();
    }

    public record Entry(String storageKey, String title, String author, String language, String sourceUrl) {}
}
