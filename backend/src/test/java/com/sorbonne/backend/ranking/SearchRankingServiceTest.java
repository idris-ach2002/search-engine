package com.sorbonne.backend.ranking;

import com.sorbonne.backend.ranking.repository.SearchRankRepository;
import com.sorbonne.backend.ranking.service.SearchRankingService;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRankingServiceTest {
    @Test
    void combinesLexicalRelevanceAndPageRank() {
        SearchRankingService service = new SearchRankingService(
                new FakeRankRepository(true, Map.of(1L, 0.1, 2L, 0.4)),
                new RankingProperties(0.85, 1e-10, 100, 0.85, 0.15)
        );

        var ranked = service.rank(List.of(
                candidate(1, 100),
                candidate(2, 95)
        ));

        assertTrue(ranked.pageRankApplied());
        assertEquals(2L, ranked.results().getFirst().bookId());
        assertEquals(0.15, ranked.pageRankWeight(), 1e-12);
        assertTrue(ranked.results().getFirst().finalScore() > ranked.results().get(1).finalScore());
    }

    @Test
    void fallsBackToPureLexicalRankingWhenPageRankIsStale() {
        SearchRankingService service = new SearchRankingService(
                new FakeRankRepository(false, Map.of(2L, 1.0)),
                new RankingProperties(0.85, 1e-10, 100, 0.85, 0.15)
        );

        var ranked = service.rank(List.of(
                candidate(1, 100),
                candidate(2, 95)
        ));

        assertFalse(ranked.pageRankApplied());
        assertEquals(1L, ranked.results().getFirst().bookId());
        assertEquals(1.0, ranked.lexicalWeight(), 1e-12);
        assertEquals(0.0, ranked.pageRankWeight(), 1e-12);
    }

    private static SearchRankingService.Candidate candidate(long id, long lexicalScore) {
        return new SearchRankingService.Candidate(
                id, (int) id, "Book " + id, "Author", "en", 10000, lexicalScore, 1
        );
    }

    private static final class FakeRankRepository implements SearchRankRepository {
        private final boolean current;
        private final Map<Long, Double> scores;

        private FakeRankRepository(boolean current, Map<Long, Double> scores) {
            this.current = current;
            this.scores = scores;
        }

        @Override
        public boolean hasCurrentScores() {
            return current;
        }

        @Override
        public Map<Long, Double> loadScores(Collection<Long> bookIds) {
            Map<Long, Double> result = new HashMap<>();
            for (Long id : bookIds) {
                if (scores.containsKey(id)) {
                    result.put(id, scores.get(id));
                }
            }
            return result;
        }

        @Override
        public double maxScore() {
            return scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }
    }
}
