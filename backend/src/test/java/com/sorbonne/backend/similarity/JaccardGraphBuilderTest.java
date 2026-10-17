package com.sorbonne.backend.similarity;

import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository.PairIntersection;
import com.sorbonne.backend.similarity.service.JaccardGraphBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JaccardGraphBuilderTest {
    private final JaccardGraphBuilder builder = new JaccardGraphBuilder();

    @Test void computesJaccardAndKeepsTopNeighbors() {
        var result = builder.build(Map.of(1L,4, 2L,4, 3L,3),
                List.of(new PairIntersection(1,2,2), new PairIntersection(1,3,1), new PairIntersection(2,3,2)), 0.0, 1);
        assertEquals(3, result.candidatePairs());
        assertEquals(3, result.qualifyingPairs());
        assertEquals(2, result.edges().size());
        var ab = result.edges().stream().filter(e -> e.bookAId()==1 && e.bookBId()==2).findFirst().orElseThrow();
        assertEquals(2.0/6.0, ab.score(), 1e-12);
        assertTrue(result.edges().stream().anyMatch(e -> e.bookAId()==2 && e.bookBId()==3));
    }

    @Test void filtersLowSimilarityPairs() {
        var result = builder.build(Map.of(1L,100,2L,100), List.of(new PairIntersection(1,2,1)), 0.05, 20);
        assertTrue(result.edges().isEmpty());
        assertEquals(0, result.qualifyingPairs());
    }
}
