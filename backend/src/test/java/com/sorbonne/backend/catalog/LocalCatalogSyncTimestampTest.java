package com.sorbonne.backend.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/** Regression: a legacy PostgreSQL database can have NOT NULL timestamps without defaults. */
class LocalCatalogSyncTimestampTest {
    @TempDir Path temporary;

    @Test
    void insertsTimestampsExplicitly() throws Exception {
        Path books = Files.createDirectories(temporary.resolve("books"));
        Files.writeString(books.resolve("My_book.txt"), "word ".repeat(10_100));
        var files = new LocalBookFiles(books.toString(), 100_000);
        var csv = new CatalogCsvReader(temporary.resolve("catalog.csv").toString());
        var builder = new LocalCatalogBuilder(csv, files, new LocalBookMetadataExtractor());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new LocalCatalogSyncService(jdbc, csv, builder, files,
                new LocalWordCounter(), 10_000);
        service.sync(false);

        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .map(call -> (String) call.getArgument(0))
                .anyMatch(sql -> sql.contains("INSERT INTO books")
                        && sql.contains("created_at,updated_at")
                        && sql.contains("CURRENT_TIMESTAMP,CURRENT_TIMESTAMP")));
    }
}
