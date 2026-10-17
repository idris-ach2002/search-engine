package com.sorbonne.backend.ranking.service;

import com.sorbonne.backend.ranking.RankingProperties;
import com.sorbonne.backend.ranking.model.PageRankResponses;
import com.sorbonne.backend.ranking.repository.PageRankRepository;
import com.sorbonne.backend.ranking.repository.PageRankRepository.RunState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class PageRankService {
    private final PageRankRepository repository;
    private final PageRankCalculator calculator;
    private final RankingProperties properties;

    public PageRankService(
            PageRankRepository repository,
            PageRankCalculator calculator,
            RankingProperties properties
    ) {
        this.repository = repository;
        this.calculator = calculator;
        this.properties = properties;
    }

    public PageRankResponses.Rebuild rebuild() {
        long startedAt = System.nanoTime();
        var nodes = repository.loadReadyBookIds();
        if (nodes.isEmpty()) {
            throw new IllegalStateException("At least one READY book is required");
        }

        var edges = repository.loadSimilarityEdges();
        PageRankCalculator.Result result = calculator.calculate(
                nodes,
                edges,
                properties.dampingFactor(),
                properties.tolerance(),
                properties.maxIterations()
        );

        Instant computedAt = Instant.now();
        repository.replaceScores(
                result.scores(),
                new RunState(
                        computedAt,
                        properties.dampingFactor(),
                        properties.tolerance(),
                        properties.maxIterations(),
                        result.iterations(),
                        result.converged(),
                        result.finalDelta(),
                        nodes.size(),
                        edges.size()
                )
        );

        Map<Long, Double> scores = result.scores();
        double scoreSum = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double minScore = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        return new PageRankResponses.Rebuild(
                nodes.size(),
                edges.size(),
                properties.dampingFactor(),
                properties.tolerance(),
                properties.maxIterations(),
                result.iterations(),
                result.converged(),
                result.finalDelta(),
                scoreSum,
                minScore,
                maxScore,
                elapsedMillis
        );
    }

    public PageRankResponses.Status status() {
        long readyBooks = repository.loadReadyBookIds().size();
        long similarityEdges = repository.countSimilarityEdges();
        long storedScores = repository.countScores();
        Optional<RunState> state = repository.loadState();
        Instant similarityBuiltAt = repository.lastSimilarityBuiltAt();

        boolean available = state.isPresent() && storedScores == readyBooks && readyBooks > 0;
        boolean stale = !available || (
                similarityBuiltAt != null
                        && state.map(RunState::computedAt).map(t -> t.isBefore(similarityBuiltAt)).orElse(true)
        );

        return new PageRankResponses.Status(
                readyBooks,
                similarityEdges,
                storedScores,
                available,
                stale,
                state.map(RunState::computedAt).orElse(null),
                similarityBuiltAt,
                properties.dampingFactor(),
                properties.tolerance(),
                properties.maxIterations(),
                state.map(RunState::iterations).orElse(null),
                state.map(RunState::converged).orElse(null),
                state.map(RunState::finalDelta).orElse(null),
                properties.normalizedLexicalWeight(),
                properties.normalizedPageRankWeight()
        );
    }

    public PageRankResponses.Top top(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        var books = repository.topScores(limit).stream()
                .map(value -> new PageRankResponses.RankedBook(
                        value.bookId(),
                        value.gutenbergId(),
                        value.title(),
                        value.author(),
                        value.score()
                ))
                .toList();
        return new PageRankResponses.Top(books);
    }
}
