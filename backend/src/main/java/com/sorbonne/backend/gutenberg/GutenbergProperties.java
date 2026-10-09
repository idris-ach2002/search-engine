package com.sorbonne.backend.gutenberg;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gutenberg")
public record GutenbergProperties(
        String harvestUrl,
        int minimumWordCount,
        long downloadDelayMs,
        long maxUncompressedBytes,
        Api api) {

    public record Api(
            String baseUrl,
            String host,
            String key,
            int reservedRequests) {
    }
}
