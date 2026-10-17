package com.sorbonne.backend.recommendation.web;

import com.sorbonne.backend.recommendation.model.SuggestionResponses;
import com.sorbonne.backend.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SuggestionController {
    private final RecommendationService recommendationService;

    public SuggestionController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/search/suggestions")
    public SuggestionResponses.SearchSuggestions simpleSearchSuggestions(
            @RequestParam String q,
            @RequestParam(required = false) Integer seedCount,
            @RequestParam(required = false) Integer limit
    ) {
        return recommendationService.simpleSearchSuggestions(q, seedCount, limit);
    }

    @GetMapping("/search/advanced/suggestions")
    public SuggestionResponses.SearchSuggestions advancedSearchSuggestions(
            @RequestParam String regex,
            @RequestParam(required = false) Integer seedCount,
            @RequestParam(required = false) Integer limit
    ) {
        return recommendationService.advancedSearchSuggestions(regex, seedCount, limit);
    }

    @GetMapping("/books/{id}/suggestions")
    public SuggestionResponses.BookSuggestions bookSuggestions(
            @PathVariable long id,
            @RequestParam(required = false) Integer limit
    ) {
        return recommendationService.bookSuggestions(id, limit);
    }
}
