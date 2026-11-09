package com.sorbonne.backend.catalog;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional explicit TXT header metadata, or a legible fallback label from the filename. */
@Component
public final class LocalBookMetadataExtractor {
    private static final int HEADER_BYTES = 32768;
    private static final Pattern TITLE = Pattern.compile("(?im)^(?:Title|Titre):[ \\t]*(.+)$");
    private static final Pattern AUTHOR = Pattern.compile("(?im)^(?:Author|Auteur):[ \\t]*(.+)$");
    private static final Pattern LANGUAGE = Pattern.compile("(?im)^(?:Language|Langue):[ \\t]*(.+)$");
    private static final Pattern OPAQUE_KEY = Pattern.compile("(?i)^[0-9a-f]{32,128}$");

    public CatalogCsvReader.Entry infer(String storageKey, Path file) {
        String title = null;
        String author = null;
        String language = null;
        try (InputStream input = Files.newInputStream(file)) {
            String header = new String(input.readNBytes(HEADER_BYTES), StandardCharsets.UTF_8);
            // Only structured, explicit metadata is treated as metadata; never use a random first line.
            title = match(TITLE, header);
            author = match(AUTHOR, header);
            language = languageCode(match(LANGUAGE, header));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect local book: " + storageKey, failure);
        }
        if (title == null) title = filenameLabel(storageKey);
        return new CatalogCsvReader.Entry(storageKey, title, author, language, null);
    }

    static String filenameLabel(String key) {
        String filename = Path.of(key).getFileName().toString();
        String stem = filename.substring(0, filename.length() - 4); // .txt
        if (OPAQUE_KEY.matcher(stem).matches()) {
            // No way to recover a book's real title from its SHA-256 storage key.
            return "Titre inconnu [" + stem.substring(0, 12) + "]";
        }
        String label = stem.replace('_', ' ').replaceAll("\\s+", " ").strip();
        return label.isBlank() ? "Livre sans titre" : label;
    }

    static String languageCode(String raw) {
        if (raw == null) return null;
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "en", "english", "anglais" -> "en";
            case "fr", "french", "français", "francais" -> "fr";
            case "de", "german", "allemand" -> "de";
            case "es", "spanish", "espagnol" -> "es";
            case "it", "italian", "italien" -> "it";
            default -> null;
        };
    }

    private static String match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        String result = matcher.group(1).strip();
        return result.isEmpty() ? null : result;
    }
}
