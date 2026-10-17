package com.sorbonne.backend.ranking.service;

import com.sorbonne.backend.ranking.repository.PageRankRepository.WeightedEdge;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PageRankCalculator {

    public Result calculate(
            List<Long> nodeIds,
            List<WeightedEdge> edges,
            double dampingFactor,
            double tolerance,
            int maxIterations
    ) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return new Result(Map.of(), 0, true, 0.0);
        }
        if (dampingFactor <= 0.0 || dampingFactor >= 1.0) {
            throw new IllegalArgumentException("dampingFactor must be in (0,1)");
        }
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }

        List<Long> nodes = nodeIds.stream().distinct().sorted().toList();
        int n = nodes.size();
        Map<Long, List<Neighbor>> adjacency = new HashMap<>();
        Map<Long, Double> outgoingWeights = new HashMap<>();
        for (Long node : nodes) {
            adjacency.put(node, new ArrayList<>());
            outgoingWeights.put(node, 0.0);
        }

        if (edges != null) {
            for (WeightedEdge edge : edges) {
                if (edge.weight() <= 0.0 || edge.bookAId() == edge.bookBId()) {
                    continue;
                }
                if (!adjacency.containsKey(edge.bookAId()) || !adjacency.containsKey(edge.bookBId())) {
                    continue;
                }
                adjacency.get(edge.bookAId()).add(new Neighbor(edge.bookBId(), edge.weight()));
                adjacency.get(edge.bookBId()).add(new Neighbor(edge.bookAId(), edge.weight()));
                outgoingWeights.merge(edge.bookAId(), edge.weight(), Double::sum);
                outgoingWeights.merge(edge.bookBId(), edge.weight(), Double::sum);
            }
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        double initial = 1.0 / n;
        for (Long node : nodes) {
            scores.put(node, initial);
        }

        int iterations = 0;
        boolean converged = false;
        double finalDelta = Double.POSITIVE_INFINITY;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            double danglingMass = 0.0;
            for (Long node : nodes) {
                if (outgoingWeights.get(node) == 0.0) {
                    danglingMass += scores.get(node);
                }
            }

            double base = (1.0 - dampingFactor) / n;
            double danglingContribution = dampingFactor * danglingMass / n;
            Map<Long, Double> next = new LinkedHashMap<>();
            for (Long node : nodes) {
                next.put(node, base + danglingContribution);
            }

            for (Long source : nodes) {
                double outgoing = outgoingWeights.get(source);
                if (outgoing == 0.0) {
                    continue;
                }
                double sourceFactor = dampingFactor * scores.get(source) / outgoing;
                for (Neighbor neighbor : adjacency.get(source)) {
                    next.merge(neighbor.bookId(), sourceFactor * neighbor.weight(), Double::sum);
                }
            }

            double delta = 0.0;
            for (Long node : nodes) {
                delta += Math.abs(next.get(node) - scores.get(node));
            }

            scores = next;
            iterations = iteration;
            finalDelta = delta;
            if (delta <= tolerance) {
                converged = true;
                break;
            }
        }

        return new Result(Map.copyOf(scores), iterations, converged, finalDelta);
    }

    private record Neighbor(long bookId, double weight) {}

    public record Result(
            Map<Long, Double> scores,
            int iterations,
            boolean converged,
            double finalDelta
    ) {}
}
