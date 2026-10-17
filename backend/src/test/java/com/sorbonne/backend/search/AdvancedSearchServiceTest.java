package com.sorbonne.backend.search;

import com.sorbonne.backend.search.model.AdvancedSearchResponse;
import com.sorbonne.backend.search.repository.SearchIndexRepository;
import com.sorbonne.backend.search.repository.SearchIndexRepository.BookPosting;
import com.sorbonne.backend.search.repository.SearchIndexRepository.IndexedTerm;
import com.sorbonne.backend.search.service.AdvancedSearchService;
import com.sorbonne.backend.search.service.RegexPatternNormalizer;
import com.sorbonne.backend.search.service.RegexTermMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedSearchServiceTest {

    @Test
    void aggregatesRegexMatchesRanksBooksAndPaginates() {
        AdvancedSearchService service = new AdvancedSearchService(
                new FakeSearchIndexRepository(),
                new RegexTermMatcher(new RegexPatternNormalizer())
        );

        AdvancedSearchResponse firstPage = service.search("m(an|en)", 0, 1);

        assertEquals("m(an|en)", firstPage.normalizedRegex());
        assertEquals(3, firstPage.matchedIndexTerms());
        assertEquals(2, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(1, firstPage.content().size());
        assertEquals(20L, firstPage.content().getFirst().bookId());
        assertEquals(10L, firstPage.content().getFirst().lexicalScore());
        assertTrue(firstPage.minimizedDfaStates() <= firstPage.dfaStates());

        AdvancedSearchResponse secondPage = service.search("m(an|en)", 1, 1);
        assertEquals(10L, secondPage.content().getFirst().bookId());
        assertEquals(7L, secondPage.content().getFirst().lexicalScore());
    }

    @Test
    void returnsEmptyPageWhenRegexMatchesNoIndexedTerm() {
        AdvancedSearchService service = new AdvancedSearchService(
                new FakeSearchIndexRepository(),
                new RegexTermMatcher(new RegexPatternNormalizer())
        );

        AdvancedSearchResponse response = service.search("z.*z", 0, 20);

        assertEquals(0, response.matchedIndexTerms());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.content().isEmpty());
    }

    private static final class FakeSearchIndexRepository implements SearchIndexRepository {
        @Override
        public List<IndexedTerm> loadTerms() {
            return List.of(
                    new IndexedTerm(1, "man"),
                    new IndexedTerm(2, "mankind"),
                    new IndexedTerm(3, "men"),
                    new IndexedTerm(4, "dog")
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
                    new BookPosting(20, 1720, "The Man Who Knew Too Much", "G.K. Chesterton", "en", 62794, 1, 8),
                    new BookPosting(20, 1720, "The Man Who Knew Too Much", "G.K. Chesterton", "en", 62794, 3, 2)
            );
        }
    }
}
