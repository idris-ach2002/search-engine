package com.sorbonne.backend.search;

import com.sorbonne.backend.indexing.service.TextNormalizer;
import com.sorbonne.backend.ranking.RankingProperties;
import com.sorbonne.backend.ranking.repository.SearchRankRepository;
import com.sorbonne.backend.ranking.service.SearchRankingService;
import com.sorbonne.backend.search.model.SimpleSearchResponse;
import com.sorbonne.backend.search.repository.SearchIndexRepository;
import com.sorbonne.backend.search.repository.SearchIndexRepository.BookPosting;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.backend.search.service.KmpTermMatcher;
import com.sorbonne.backend.search.service.SimpleSearchService;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleSearchServiceTest {

    @Test
    void aggregatesOccurrencesRanksBooksAndPaginates() {
        SearchIndexRepository repository = new FakeSearchIndexRepository();
        SimpleSearchService service = new SimpleSearchService(
                repository,
                new KmpTermMatcher(new TextNormalizer()),
                lexicalOnlyRanking()
        );

        SimpleSearchResponse firstPage = service.search("man", 0, 1);

        assertEquals("man", firstPage.normalizedQuery());
        assertEquals(2, firstPage.matchedIndexTerms());
        assertEquals(2, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertFalse(firstPage.pageRankApplied());
        assertEquals(1.0, firstPage.lexicalWeight(), 1e-12);
        assertEquals(1, firstPage.content().size());
        assertEquals(20L, firstPage.content().getFirst().bookId());
        assertEquals(8L, firstPage.content().getFirst().lexicalScore());
        assertEquals(1.0, firstPage.content().getFirst().finalScore(), 1e-12);
        assertEquals(1, firstPage.content().getFirst().matchedTerms());

        SimpleSearchResponse secondPage = service.search("man", 1, 1);
        assertEquals(10L, secondPage.content().getFirst().bookId());
        assertEquals(7L, secondPage.content().getFirst().lexicalScore());
        assertEquals(2, secondPage.content().getFirst().matchedTerms());
    }

    @Test
    void returnsAnEmptyPageWhenKmpMatchesNoIndexedTerm() {
        SimpleSearchService service = new SimpleSearchService(
                new FakeSearchIndexRepository(),
                new KmpTermMatcher(new TextNormalizer()),
                lexicalOnlyRanking()
        );

        SimpleSearchResponse response = service.search("zebra", 0, 20);

        assertEquals(0, response.matchedIndexTerms());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.content().isEmpty());
    }

    private static SearchRankingService lexicalOnlyRanking() {
        return new SearchRankingService(
                new SearchRankRepository() {
                    @Override public boolean hasCurrentScores() { return false; }
                    @Override public Map<Long, Double> loadScores(Collection<Long> ids) { return Map.of(); }
                    @Override public double maxScore() { return 0.0; }
                },
                new RankingProperties(0.85, 1e-10, 100, 0.85, 0.15)
        );
    }

    private static final class FakeSearchIndexRepository implements SearchIndexRepository {
        @Override
        public List<IndexedTerm> loadTerms() {
            return List.of(
                    new IndexedTerm(1, "man"),
                    new IndexedTerm(2, "mankind"),
                    new IndexedTerm(3, "dog")
            );
        }

        @Override
        public List<BookPosting> loadReadyBookPostings(List<Long> termIds) {
            if (termIds.isEmpty()) {
                return List.of();
            }
            return List.of(
                    new BookPosting(10, 10084, "Kazan", "James Oliver Curwood", "en", 69489, 1, 5),
                    new BookPosting(10, 10084, "Kazan", "James Oliver Curwood", "en", 69489, 2, 2),
                    new BookPosting(20, 1720, "The Man Who Knew Too Much", "G.K. Chesterton", "en", 62794, 1, 8)
            );
        }
    }
}
