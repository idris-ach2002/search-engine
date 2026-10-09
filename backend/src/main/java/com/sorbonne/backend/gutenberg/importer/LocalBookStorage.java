package com.sorbonne.backend.gutenberg.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.sorbonne.backend.config.StorageProperties;

@Component
public class LocalBookStorage {

    private final Path root;

    public LocalBookStorage(StorageProperties properties) throws IOException {
        this.root = Path.of(properties.booksRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredBook save(int gutenbergId, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String storageKey = gutenbergId + ".txt";
        Path target = resolve(storageKey);

        Path temp = Files.createTempFile(root, gutenbergId + "-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }

        // Persist only the portable key, never the machine-specific absolute path.
        return new StoredBook(storageKey, sha256(bytes), bytes.length);
    }

    /**
     * Resolves a persisted storage key against the local storage root.
     * This is the only place that translates portable DB data into a machine path.
     */
    public Path resolve(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IOException("Storage key must not be blank");
        }

        Path key = Path.of(storageKey);
        if (key.isAbsolute()) {
            throw new IOException("Storage key must be relative");
        }

        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        return resolved;
    }

    public String read(String storageKey) throws IOException {
        return Files.readString(resolve(storageKey), StandardCharsets.UTF_8);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record StoredBook(String storageKey, String sha256, long bytes) {
    }
}
