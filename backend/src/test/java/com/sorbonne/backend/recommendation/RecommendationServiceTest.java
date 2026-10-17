package com.sorbonne.backend.recommendation;

import com.sorbonne.backend.book.BookRepository;
import com.sorbonne.backend.recommendation.service.RecommendationService;
import com.sorbonne.backend.recommendation.service.SuggestionAggregator;
import com.sorbonne.backend.search.model.SimpleSearchResponse;
import com.sorbonne.backend.search.model.SimpleSearchResult;
import com.sorbonne.backend.search.service.AdvancedSearchService;
import com.sorbonne.backend.search.service.SimpleSearchService;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {
    @Test
    void buildsSuggestionsFromTopSimpleSearchResults() {
        SimpleSearchService simple = mock(SimpleSearchService.class);
        AdvancedSearchService advanced = mock(AdvancedSearchService.class);
        SimilarityGraphRepository similarity = mock(SimilarityGraphRepository.class);
        BookRepository books = mock(BookRepository.class);
        SuggestionProperties properties = new SuggestionProperties(3, 10, 20, 5, 50);
        RecommendationService service = new RecommendationService(simple, advanced, similarity, books, properties, new SuggestionAggregator());

        SimpleSearchResult seed = new SimpleSearchResult(10, 1720, "Seed", "Author", "en", 10000, 100, 3, 1.0, 0.2, 1.0, 1.0);
        when(simple.search("man", 0, 3)).thenReturn(new SimpleSearchResponse(
                "man", "man", 0, 3, 1, 1, 2, true, 0.85, 0.15, 1, List.of(seed)
        ));
        when(similarity.findNeighbors(eq(10L), anyInt())).thenReturn(List.of(
                new SimilarityGraphRepository.Neighbor(20, 2020, "Neighbor", "Other", 0.4, 20, 50)
        ));

        var response = service.simpleSearchSuggestions("man", null, null);

        assertEquals("simple", response.mode());
        assertEquals(1, response.usedSeedCount());
        assertEquals(1, response.suggestions().size());
        assertEquals(20L, response.suggestions().getFirst().bookId());
        assertEquals(0.4, response.suggestions().getFirst().recommendationScore(), 1.0e-12);
    }
}
