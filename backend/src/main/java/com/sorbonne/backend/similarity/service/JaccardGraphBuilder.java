package com.sorbonne.backend.similarity.service;

import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository.PairIntersection;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository.SimilarityEdge;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JaccardGraphBuilder {
    public BuildResult build(Map<Long,Integer> featureCounts, List<PairIntersection> intersections, double minimumSimilarity, int maxNeighborsPerBook) {
        Map<Long, PriorityQueue<SimilarityEdge>> top = new HashMap<>();
        int qualifying = 0;
        for (PairIntersection p : intersections) {
            int aCount = featureCounts.getOrDefault(p.bookAId(), 0);
            int bCount = featureCounts.getOrDefault(p.bookBId(), 0);
            int union = aCount + bCount - p.intersectionSize();
            if (union <= 0) continue;
            double score = (double) p.intersectionSize() / union;
            if (score < minimumSimilarity) continue;
            qualifying++;
            SimilarityEdge edge = new SimilarityEdge(p.bookAId(), p.bookBId(), score, p.intersectionSize(), union);
            offer(top.computeIfAbsent(p.bookAId(), k -> new PriorityQueue<>(WORST_FIRST)), edge, maxNeighborsPerBook);
            offer(top.computeIfAbsent(p.bookBId(), k -> new PriorityQueue<>(WORST_FIRST)), edge, maxNeighborsPerBook);
        }
        Map<PairKey,SimilarityEdge> selected = new HashMap<>();
        for (PriorityQueue<SimilarityEdge> q : top.values()) for (SimilarityEdge e : q) selected.put(new PairKey(e.bookAId(),e.bookBId()), e);
        List<SimilarityEdge> edges = new ArrayList<>(selected.values());
        edges.sort(Comparator.comparingLong(SimilarityEdge::bookAId).thenComparingLong(SimilarityEdge::bookBId));
        return new BuildResult(List.copyOf(edges), intersections.size(), qualifying);
    }

    private static final Comparator<SimilarityEdge> WORST_FIRST = Comparator
            .comparingDouble(SimilarityEdge::score)
            .thenComparingInt(SimilarityEdge::intersectionSize)
            .thenComparingLong(SimilarityEdge::bookAId)
            .thenComparingLong(SimilarityEdge::bookBId);

    private static void offer(PriorityQueue<SimilarityEdge> q, SimilarityEdge edge, int max) {
        if (q.size() < max) { q.add(edge); return; }
        SimilarityEdge worst = q.peek();
        if (WORST_FIRST.compare(edge, worst) > 0) { q.poll(); q.add(edge); }
    }

    private record PairKey(long a, long b) {}
    public record BuildResult(List<SimilarityEdge> edges, int candidatePairs, int qualifyingPairs) {}
}
