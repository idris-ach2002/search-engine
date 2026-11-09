package com.sorbonne.backend.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The database stores relative storage keys; metadata comes from catalog.csv, never filenames. */
@Component
public final class LocalBookFiles {
    private final Path booksRoot;
    private final long maximumBytes;

    public LocalBookFiles(
            @Value("${app.storage.books-root:./data/books}") String booksRoot,
            @Value("${app.catalog.max-text-bytes:25000000}") long maximumBytes
    ) {
        this.booksRoot = Path.of(booksRoot).toAbsolutePath().normalize();
        if (maximumBytes < 1) throw new IllegalArgumentException("max-text-bytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    /** Lists .txt files without following symlinks. Keys are portable relative paths. */
    public List<String> listStorageKeys() {
        if (!Files.isDirectory(booksRoot)) return List.of();
        try (Stream<Path> paths = Files.walk(booksRoot)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .map(path -> booksRoot.relativize(path).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot scan books directory: " + booksRoot, failure);
        }
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storage_key is required");
        }
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute() || !storageKey.endsWith(".txt")
                || java.util.stream.StreamSupport.stream(relative.spliterator(), false)
                        .anyMatch(part -> part.toString().equals("..") || part.toString().equals("."))) {
            throw new IllegalArgumentException("storage_key must be a relative .txt path");
        }
        Path full = booksRoot.resolve(relative).normalize();
        if (!full.startsWith(booksRoot) || full.equals(booksRoot)) {
            throw new IllegalArgumentException("storage_key escapes the books directory: " + storageKey);
        }
        try {
            Path realRoot = booksRoot.toRealPath();
            Path realFile = full.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(full, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Not a regular file inside books directory: " + storageKey);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Missing or unreadable book: " + storageKey, failure);
        }
        return full;
    }

    public StoredText read(String storageKey) {
        Path path = resolve(storageKey);
        try {
            if (Files.size(path) > maximumBytes) {
                throw new IllegalArgumentException("Book is larger than max-text-bytes: " + storageKey);
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > maximumBytes) {
                throw new IllegalArgumentException("Book is larger than max-text-bytes: " + storageKey);
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException failure) {
                throw new IllegalArgumentException("Book must contain UTF-8 text: " + storageKey, failure);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new StoredText(text, bytes.length, HexFormat.of().formatHex(digest.digest(bytes)));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot read book: " + storageKey, failure);
        }
    }

    public record StoredText(String text, long contentBytes, String sha256) {}
}
