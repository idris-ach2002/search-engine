package com.sorbonne.backend.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.suggestions")
public record SuggestionProperties(
        int defaultSeedCount,
        int defaultLimit,
        int neighborsPerSeed,
        int maxSeedCount,
        int maxLimit
) {
    public SuggestionProperties {
        if (defaultSeedCount < 1) throw new IllegalArgumentException("defaultSeedCount must be positive");
        if (defaultLimit < 1) throw new IllegalArgumentException("defaultLimit must be positive");
        if (neighborsPerSeed < 1 || neighborsPerSeed > 100) throw new IllegalArgumentException("neighborsPerSeed must be between 1 and 100");
        if (maxSeedCount < defaultSeedCount) throw new IllegalArgumentException("maxSeedCount must be >= defaultSeedCount");
        if (maxLimit < defaultLimit) throw new IllegalArgumentException("maxLimit must be >= defaultLimit");
        if (maxSeedCount > 10) throw new IllegalArgumentException("maxSeedCount must be <= 10");
        if (maxLimit > 100) throw new IllegalArgumentException("maxLimit must be <= 100");
    }

    public int resolveSeedCount(Integer requested) {
        int value = requested == null ? defaultSeedCount : requested;
        if (value < 1 || value > maxSeedCount) {
            throw new IllegalArgumentException("seedCount must be between 1 and " + maxSeedCount);
        }
        return value;
    }

    public int resolveLimit(Integer requested) {
        int value = requested == null ? defaultLimit : requested;
        if (value < 1 || value > maxLimit) {
            throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
        }
        return value;
    }
}
