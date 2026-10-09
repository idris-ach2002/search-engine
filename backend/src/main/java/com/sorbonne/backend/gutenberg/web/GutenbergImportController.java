package com.sorbonne.backend.gutenberg.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sorbonne.backend.gutenberg.api.GutenbergApiClient;
import com.sorbonne.backend.gutenberg.api.GutenbergApiClient.ApiResult;
import com.sorbonne.backend.gutenberg.api.GutenbergApiDtos.BooksPage;
import com.sorbonne.backend.gutenberg.api.GutenbergQuotaSnapshot;
import com.sorbonne.backend.gutenberg.api.GutenbergQuotaTracker;
import com.sorbonne.backend.gutenberg.importer.GutenbergImportService;
import com.sorbonne.backend.gutenberg.importer.GutenbergImportService.ImportBatchResult;
import com.sorbonne.backend.gutenberg.importer.GutenbergImportService.ImportStatus;

@RestController
@RequestMapping("/api/admin/gutenberg")
public class GutenbergImportController {

    private final GutenbergImportService importService;
    private final GutenbergApiClient apiClient;
    private final GutenbergQuotaTracker quotaTracker;

    public GutenbergImportController(
            GutenbergImportService importService,
            GutenbergApiClient apiClient,
            GutenbergQuotaTracker quotaTracker) {
        this.importService = importService;
        this.apiClient = apiClient;
        this.quotaTracker = quotaTracker;
    }

    @PostMapping("/import/batch")
    @ResponseStatus(HttpStatus.OK)
    public ImportBatchResult importBatch(
            @RequestParam(defaultValue = "1664") int target,
            @RequestParam(defaultValue = "10") int maxDownloads)
            throws IOException, InterruptedException {
        return importService.importBatch(target, maxDownloads);
    }

    @GetMapping("/import/status")
    public ImportStatus status() {
        return importService.status();
    }

    @GetMapping("/rapidapi/quota")
    public GutenbergQuotaSnapshot quota() {
        return quotaTracker.latest();
    }

    /**
     * Intentionally consumes one RapidAPI request. It is provided only to verify
     * the API key / catalog integration and inspect the quota headers.
     */
    @PostMapping("/rapidapi/probe")
    public ApiResult<BooksPage> probeRapidApi(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String query) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        return apiClient.getBooks(safePage, safeSize, query);
    }
}
