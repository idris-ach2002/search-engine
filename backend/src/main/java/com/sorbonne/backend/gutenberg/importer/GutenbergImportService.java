package com.sorbonne.backend.gutenberg.importer;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sorbonne.backend.book.Book;
import com.sorbonne.backend.book.BookRepository;
import com.sorbonne.backend.book.BookStatus;
import com.sorbonne.backend.gutenberg.GutenbergProperties;
import com.sorbonne.backend.gutenberg.harvest.GutenbergHarvestClient;
import com.sorbonne.backend.gutenberg.harvest.GutenbergHarvestClient.HarvestLink;
import com.sorbonne.backend.gutenberg.harvest.GutenbergHarvestClient.HarvestPage;
import com.sorbonne.backend.gutenberg.importer.GutenbergTextParser.ParsedBook;
import com.sorbonne.backend.gutenberg.importer.LocalBookStorage.StoredBook;

@Service
public class GutenbergImportService {

    private static final Logger log = LoggerFactory.getLogger(GutenbergImportService.class);
    private static final String SOURCE = "GUTENBERG_HARVEST_EN";

    private final BookRepository bookRepository;
    private final GutenbergImportCheckpointRepository checkpointRepository;
    private final GutenbergHarvestClient harvestClient;
    private final GutenbergTextParser textParser;
    private final WordCounter wordCounter;
    private final LocalBookStorage storage;
    private final GutenbergProperties properties;

    public GutenbergImportService(
            BookRepository bookRepository,
            GutenbergImportCheckpointRepository checkpointRepository,
            GutenbergHarvestClient harvestClient,
            GutenbergTextParser textParser,
            WordCounter wordCounter,
            LocalBookStorage storage,
            GutenbergProperties properties) {
        this.bookRepository = bookRepository;
        this.checkpointRepository = checkpointRepository;
        this.harvestClient = harvestClient;
        this.textParser = textParser;
        this.wordCounter = wordCounter;
        this.storage = storage;
        this.properties = properties;
    }

    public synchronized ImportBatchResult importBatch(int targetReadyBooks, int maxDownloads)
            throws IOException, InterruptedException {

        if (targetReadyBooks < 1) {
            throw new IllegalArgumentException("targetReadyBooks must be >= 1");
        }
        if (maxDownloads < 1 || maxDownloads > 100) {
            throw new IllegalArgumentException("maxDownloads must be between 1 and 100");
        }

        long readyBefore = bookRepository.countByStatus(BookStatus.READY);
        long ready = readyBefore;
        int attempted = 0;
        int accepted = 0;
        int rejected = 0;
        int failed = 0;
        int skippedExisting = 0;

        GutenbergImportCheckpoint checkpoint = checkpointRepository.findById(SOURCE)
                .orElseGet(() -> checkpointRepository.save(
                        new GutenbergImportCheckpoint(SOURCE, properties.harvestUrl())));

        while (ready < targetReadyBooks && attempted < maxDownloads) {
            HarvestPage page = harvestClient.fetchPage(checkpoint.getPageUrl());

            if (checkpoint.getLinkIndex() >= page.links().size()) {
                if (page.nextPageUrl() == null) {
                    break;
                }
                checkpoint.moveToNextPage(page.nextPageUrl());
                checkpointRepository.save(checkpoint);
                continue;
            }

            HarvestLink link = page.links().get(checkpoint.getLinkIndex());
            Optional<Book> existingBook = bookRepository.findByGutenbergId(link.gutenbergId());

            if (existingBook.isPresent() && existingBook.get().getStatus() != BookStatus.FAILED) {
                skippedExisting++;
                advanceCheckpoint(checkpoint, page);
                continue;
            }

            attempted++;

            try {
                if (properties.downloadDelayMs() > 0) {
                    Thread.sleep(properties.downloadDelayMs());
                }

                String rawText = harvestClient.downloadText(link);
                ParsedBook parsed = textParser.parse(link.gutenbergId(), link.url(), rawText);
                int wordCount = wordCounter.count(parsed.text());

                Book book = existingBook.orElseGet(() -> new Book(
                        parsed.gutenbergId(),
                        parsed.title(),
                        parsed.author(),
                        parsed.language(),
                        parsed.sourceUrl()));
                book.updateMetadata(
                        parsed.title(),
                        parsed.author(),
                        parsed.language(),
                        parsed.sourceUrl());

                if (wordCount >= properties.minimumWordCount()) {
                    StoredBook stored = storage.save(parsed.gutenbergId(), parsed.text());
                    book.markReady(wordCount, stored.storageKey(), stored.sha256(), stored.bytes());
                    accepted++;
                    ready++;
                } else {
                    book.markRejectedTooShort(wordCount);
                    rejected++;
                }

                bookRepository.save(book);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                failed++;
                log.warn("Failed to import Gutenberg book {} from {}",
                        link.gutenbergId(), link.url(), exception);

                Book failedBook = existingBook.orElseGet(() -> new Book(
                        link.gutenbergId(),
                        "Gutenberg book " + link.gutenbergId(),
                        null,
                        "en",
                        link.url()));
                failedBook.markFailed();
                bookRepository.save(failedBook);
            }

            advanceCheckpoint(checkpoint, page);
        }

        return new ImportBatchResult(
                targetReadyBooks,
                readyBefore,
                ready,
                attempted,
                accepted,
                rejected,
                failed,
                skippedExisting,
                checkpoint.getPageUrl(),
                checkpoint.getLinkIndex(),
                properties.minimumWordCount());
    }

    public ImportStatus status() {
        GutenbergImportCheckpoint checkpoint = checkpointRepository.findById(SOURCE).orElse(null);
        return new ImportStatus(
                bookRepository.countByStatus(BookStatus.READY),
                bookRepository.countByStatus(BookStatus.REJECTED_TOO_SHORT),
                bookRepository.countByStatus(BookStatus.FAILED),
                checkpoint == null ? properties.harvestUrl() : checkpoint.getPageUrl(),
                checkpoint == null ? 0 : checkpoint.getLinkIndex(),
                properties.minimumWordCount());
    }

    private void advanceCheckpoint(GutenbergImportCheckpoint checkpoint, HarvestPage page) {
        int nextIndex = checkpoint.getLinkIndex() + 1;
        if (nextIndex >= page.links().size() && page.nextPageUrl() != null) {
            checkpoint.moveToNextPage(page.nextPageUrl());
        } else {
            checkpoint.moveWithinPage(nextIndex);
        }
        checkpointRepository.save(checkpoint);
    }

    public record ImportBatchResult(
            int targetReadyBooks,
            long readyBefore,
            long readyAfter,
            int attemptedDownloads,
            int accepted,
            int rejectedTooShort,
            int failed,
            int skippedExisting,
            String nextPageUrl,
            int nextLinkIndex,
            int minimumWordCount) {
    }

    public record ImportStatus(
            long readyBooks,
            long rejectedTooShort,
            long failed,
            String nextPageUrl,
            int nextLinkIndex,
            int minimumWordCount) {
    }
}
