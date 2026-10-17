package com.sorbonne.backend.indexing.web;

import com.sorbonne.backend.indexing.service.BookIndexingService;
import com.sorbonne.backend.indexing.service.BookIndexingWorker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/indexing")
public class IndexingAdminController {

    private final BookIndexingService indexingService;

    public IndexingAdminController(BookIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/status")
    public BookIndexingService.IndexingStatus status() {
        return indexingService.status();
    }

    @PostMapping("/books/{bookId}")
    public BookIndexingWorker.IndexedBook indexBook(@PathVariable long bookId) {
        return indexingService.indexBook(bookId);
    }

    @PostMapping("/all")
    public BookIndexingService.BatchResult indexPending(
            @RequestParam(defaultValue = "50") int maxBooks
    ) {
        return indexingService.indexPending(maxBooks);
    }
}
