package com.sorbonne.backend.ranking;

import com.sorbonne.backend.ranking.repository.PageRankRepository.WeightedEdge;
import com.sorbonne.backend.ranking.service.PageRankCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRankCalculatorTest {
    private final PageRankCalculator calculator = new PageRankCalculator();

    @Test
    void givesEqualScoresToTwoSymmetricNodes() {
        var result = calculator.calculate(
                List.of(1L, 2L),
                List.of(new WeightedEdge(1, 2, 0.7)),
                0.85,
                1e-12,
                200
        );

        assertTrue(result.converged());
        assertEquals(0.5, result.scores().get(1L), 1e-10);
        assertEquals(0.5, result.scores().get(2L), 1e-10);
        assertEquals(1.0, result.scores().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
    }

    @Test
    void ranksTheCenterOfAChainAboveItsLeaves() {
        var result = calculator.calculate(
                List.of(1L, 2L, 3L),
                List.of(
                        new WeightedEdge(1, 2, 1.0),
                        new WeightedEdge(2, 3, 1.0)
                ),
                0.85,
                1e-12,
                200
        );

        assertTrue(result.converged());
        assertTrue(result.scores().get(2L) > result.scores().get(1L));
        assertEquals(result.scores().get(1L), result.scores().get(3L), 1e-10);
        assertEquals(1.0, result.scores().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
    }

    @Test
    void redistributesDanglingMassToIsolatedNodes() {
        var result = calculator.calculate(
                List.of(1L, 2L, 3L),
                List.of(new WeightedEdge(1, 2, 1.0)),
                0.85,
                1e-12,
                200
        );

        assertTrue(result.scores().get(3L) > 0.0);
        assertEquals(1.0, result.scores().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
    }
}
