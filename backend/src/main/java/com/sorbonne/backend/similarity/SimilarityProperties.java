package com.sorbonne.backend.similarity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.similarity")
public record SimilarityProperties(
        int minDocumentFrequency,
        double maxDocumentFrequencyRatio,
        int minimumMaxDocumentFrequency,
        int maxDocumentFrequencyAbsolute,
        int minTermLength,
        int minimumSharedTerms,
        double minimumSimilarity,
        int maxNeighborsPerBook
) {
    public SimilarityProperties {
        if (minDocumentFrequency < 2) throw new IllegalArgumentException("minDocumentFrequency must be >= 2");
        if (maxDocumentFrequencyRatio <= 0 || maxDocumentFrequencyRatio > 1) throw new IllegalArgumentException("maxDocumentFrequencyRatio must be in (0,1]");
        if (minimumMaxDocumentFrequency < minDocumentFrequency) throw new IllegalArgumentException("minimumMaxDocumentFrequency too small");
        if (maxDocumentFrequencyAbsolute < minimumMaxDocumentFrequency) throw new IllegalArgumentException("maxDocumentFrequencyAbsolute too small");
        if (minTermLength < 1) throw new IllegalArgumentException("minTermLength must be positive");
        if (minimumSharedTerms < 1) throw new IllegalArgumentException("minimumSharedTerms must be positive");
        if (minimumSimilarity < 0 || minimumSimilarity > 1) throw new IllegalArgumentException("minimumSimilarity must be in [0,1]");
        if (maxNeighborsPerBook < 1) throw new IllegalArgumentException("maxNeighborsPerBook must be positive");
    }

    public int resolveMaxDocumentFrequency(long indexedBooks) {
        int ratioLimit = (int) Math.ceil(indexedBooks * maxDocumentFrequencyRatio);
        return Math.min(maxDocumentFrequencyAbsolute, Math.max(minimumMaxDocumentFrequency, ratioLimit));
    }
}
