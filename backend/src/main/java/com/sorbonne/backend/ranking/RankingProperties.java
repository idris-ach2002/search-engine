package com.sorbonne.backend.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ranking")
public record RankingProperties(
        double dampingFactor,
        double tolerance,
        int maxIterations,
        double lexicalWeight,
        double pageRankWeight
) {
    public RankingProperties {
        if (dampingFactor <= 0.0 || dampingFactor >= 1.0) {
            throw new IllegalArgumentException("dampingFactor must be in (0,1)");
        }
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        if (lexicalWeight < 0.0 || pageRankWeight < 0.0) {
            throw new IllegalArgumentException("ranking weights must be non-negative");
        }
        if (lexicalWeight + pageRankWeight <= 0.0) {
            throw new IllegalArgumentException("at least one ranking weight must be positive");
        }
    }

    public double normalizedLexicalWeight() {
        return lexicalWeight / (lexicalWeight + pageRankWeight);
    }

    public double normalizedPageRankWeight() {
        return pageRankWeight / (lexicalWeight + pageRankWeight);
    }
}
