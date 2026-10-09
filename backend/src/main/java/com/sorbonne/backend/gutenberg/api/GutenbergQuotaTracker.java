package com.sorbonne.backend.gutenberg.api;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.sorbonne.backend.gutenberg.GutenbergProperties;

@Component
public class GutenbergQuotaTracker {

    private static final String PLAN_LIMIT = "x-ratelimit-requests-limit";
    private static final String PLAN_REMAINING = "x-ratelimit-requests-remaining";
    private static final String PLAN_RESET = "x-ratelimit-requests-reset";
    private static final String HARD_LIMIT = "x-rate-limit-rapid-free-plans-hard-limit-limit";
    private static final String HARD_REMAINING = "x-rate-limit-rapid-free-plans-hard-limit-remaining";
    private static final String HARD_RESET = "x-rate-limit-rapid-free-plans-hard-limit-reset";

    private final AtomicReference<GutenbergQuotaSnapshot> latest = new AtomicReference<>();
    private final int reservedRequests;

    public GutenbergQuotaTracker(GutenbergProperties properties) {
        this.reservedRequests = properties.api().reservedRequests();
    }

    public void assertRequestAllowed() {
        GutenbergQuotaSnapshot snapshot = latest.get();
        if (snapshot == null) {
            return;
        }

        Long remaining = snapshot.hardRemaining() != null
                ? snapshot.hardRemaining()
                : snapshot.planRemaining();

        if (remaining != null && remaining <= reservedRequests) {
            throw new GutenbergQuotaException(
                    "RapidAPI quota guard stopped the request: only " + remaining
                            + " request(s) remain and " + reservedRequests + " are reserved.");
        }
    }

    public GutenbergQuotaSnapshot update(HttpHeaders headers) {
        GutenbergQuotaSnapshot snapshot = new GutenbergQuotaSnapshot(
                longHeader(headers, PLAN_LIMIT),
                longHeader(headers, PLAN_REMAINING),
                longHeader(headers, PLAN_RESET),
                longHeader(headers, HARD_LIMIT),
                longHeader(headers, HARD_REMAINING),
                longHeader(headers, HARD_RESET),
                Instant.now());
        latest.set(snapshot);
        return snapshot;
    }

    public GutenbergQuotaSnapshot latest() {
        return latest.get();
    }

    private Long longHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
