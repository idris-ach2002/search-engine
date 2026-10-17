package com.sorbonne.backend.similarity.web;

import com.sorbonne.backend.similarity.model.SimilarityResponses;
import com.sorbonne.backend.similarity.service.SimilarityGraphService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/similarity")
public class SimilarityAdminController {
    private final SimilarityGraphService service;
    public SimilarityAdminController(SimilarityGraphService service) { this.service=service; }

    @PostMapping("/rebuild") public SimilarityResponses.Rebuild rebuild() { return service.rebuild(); }
    @GetMapping("/status") public SimilarityResponses.Status status() { return service.status(); }
    @GetMapping("/books/{bookId}/neighbors") public SimilarityResponses.Neighbors neighbors(@PathVariable long bookId, @RequestParam(defaultValue="10") int limit) { return service.neighbors(bookId,limit); }
}
