package com.sorbonne.backend.similarity.service;

import com.sorbonne.backend.book.BookRepository;
import com.sorbonne.backend.similarity.SimilarityProperties;
import com.sorbonne.backend.similarity.model.SimilarityResponses;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimilarityGraphService {
    private final SimilarityGraphRepository repository;
    private final BookRepository bookRepository;
    private final SimilarityProperties properties;
    private final SimilarityStopWords stopWords;
    private final JaccardGraphBuilder builder;

    public SimilarityGraphService(SimilarityGraphRepository repository, BookRepository bookRepository, SimilarityProperties properties, SimilarityStopWords stopWords, JaccardGraphBuilder builder) {
        this.repository=repository; this.bookRepository=bookRepository; this.properties=properties; this.stopWords=stopWords; this.builder=builder;
    }

    public SimilarityResponses.Rebuild rebuild() {
        long start = System.nanoTime();
        long ready = repository.countReadyBooks();
        long indexed = repository.countIndexedReadyBooks();
        if (indexed < 2) throw new IllegalStateException("At least two indexed READY books are required");
        int maxDf = properties.resolveMaxDocumentFrequency(indexed);
        var excluded = stopWords.values();
        long eligibleTerms = repository.countEligibleTerms(properties.minDocumentFrequency(), maxDf, properties.minTermLength(), excluded);
        long postings = repository.countEligibleFeaturePostings(properties.minDocumentFrequency(), maxDf, properties.minTermLength(), excluded);
        var counts = repository.loadFeatureCounts(properties.minDocumentFrequency(), maxDf, properties.minTermLength(), excluded);
        var intersections = repository.loadPairIntersections(properties.minDocumentFrequency(), maxDf, properties.minTermLength(), properties.minimumSharedTerms(), excluded);
        var result = builder.build(counts, intersections, properties.minimumSimilarity(), properties.maxNeighborsPerBook());
        repository.replaceEdges(result.edges());
        long elapsed = (System.nanoTime()-start)/1_000_000;
        return new SimilarityResponses.Rebuild(ready,indexed,maxDf,eligibleTerms,postings,result.candidatePairs(),result.qualifyingPairs(),result.edges().size(),properties.minimumSimilarity(),properties.maxNeighborsPerBook(),elapsed);
    }

    public SimilarityResponses.Status status() {
        return new SimilarityResponses.Status(repository.countReadyBooks(), repository.countIndexedReadyBooks(), repository.countEdges(), repository.lastBuiltAt(), properties.minDocumentFrequency(), properties.maxDocumentFrequencyRatio(), properties.minimumSimilarity(), properties.maxNeighborsPerBook());
    }

    public SimilarityResponses.Neighbors neighbors(long bookId, int limit) {
        if (!bookRepository.existsById(bookId)) throw new IllegalArgumentException("Unknown book id: " + bookId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        List<SimilarityResponses.Neighbor> values = repository.findNeighbors(bookId, limit).stream()
                .map(n -> new SimilarityResponses.Neighbor(n.bookId(), n.gutenbergId(), n.title(), n.author(), n.score(), n.intersectionSize(), n.unionSize())).toList();
        return new SimilarityResponses.Neighbors(bookId, values);
    }
}
