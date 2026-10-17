package com.sorbonne.backend.search.web;

import com.sorbonne.backend.search.model.SimpleSearchResponse;
import com.sorbonne.backend.search.service.SimpleSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SimpleSearchController {

    private final SimpleSearchService simpleSearchService;

    public SimpleSearchController(SimpleSearchService simpleSearchService) {
        this.simpleSearchService = simpleSearchService;
    }

    @GetMapping
    public SimpleSearchResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return simpleSearchService.search(q, page, size);
    }
}
