package com.sorbonne.backend.gutenberg.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.sorbonne.backend.gutenberg.GutenbergProperties;
import com.sorbonne.backend.gutenberg.api.GutenbergApiDtos.BooksPage;
import com.sorbonne.backend.gutenberg.api.GutenbergApiDtos.TextResponse;

@Component
public class GutenbergApiClient {

    private final RestClient restClient;
    private final GutenbergProperties properties;
    private final GutenbergQuotaTracker quotaTracker;

    public GutenbergApiClient(
            RestClient.Builder builder,
            GutenbergProperties properties,
            GutenbergQuotaTracker quotaTracker) {
        this.properties = properties;
        this.quotaTracker = quotaTracker;
        this.restClient = builder
                .baseUrl(properties.api().baseUrl())
                .build();
    }

    public ApiResult<BooksPage> getBooks(int page, int pageSize, String query) {
        assertConfigured();
        quotaTracker.assertRequestAllowed();

        ResponseEntity<BooksPage> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/books")
                            .queryParam("page", page)
                            .queryParam("page_size", pageSize);
                    if (query != null && !query.isBlank()) {
                        uriBuilder.queryParam("q", query);
                    }
                    return uriBuilder.build();
                })
                .header("X-RapidAPI-Key", properties.api().key())
                .header("X-RapidAPI-Host", properties.api().host())
                .retrieve()
                .toEntity(BooksPage.class);

        GutenbergQuotaSnapshot quota = quotaTracker.update(response.getHeaders());
        return new ApiResult<>(response.getBody(), quota);
    }

    public ApiResult<TextResponse> getCleanedText(int gutenbergId) {
        assertConfigured();
        quotaTracker.assertRequestAllowed();

        ResponseEntity<TextResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/books/{id}/text")
                        .queryParam("cleaning_mode", "simple")
                        .build(gutenbergId))
                .header("X-RapidAPI-Key", properties.api().key())
                .header("X-RapidAPI-Host", properties.api().host())
                .retrieve()
                .toEntity(TextResponse.class);

        GutenbergQuotaSnapshot quota = quotaTracker.update(response.getHeaders());
        return new ApiResult<>(response.getBody(), quota);
    }

    private void assertConfigured() {
        if (properties.api().key() == null || properties.api().key().isBlank()) {
            throw new IllegalStateException(
                    "GUTENBERG_API_KEY is not configured. Keep the key in an environment variable, not in Git.");
        }
    }

    public record ApiResult<T>(T body, GutenbergQuotaSnapshot quota) {
    }
}
