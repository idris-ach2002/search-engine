package com.sorbonne.backend.search.web;

import com.sorbonne.backend.search.model.AdvancedSearchResponse;
import com.sorbonne.backend.search.service.AdvancedSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/advanced")
public class AdvancedSearchController {

    private final AdvancedSearchService advancedSearchService;

    public AdvancedSearchController(AdvancedSearchService advancedSearchService) {
        this.advancedSearchService = advancedSearchService;
    }

    @GetMapping
    public AdvancedSearchResponse search(
            @RequestParam String regex,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return advancedSearchService.search(regex, page, size);
    }
}
