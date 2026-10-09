package com.sorbonne.backend.gutenberg.api;

import java.time.Instant;

public record GutenbergQuotaSnapshot(
        Long planLimit,
        Long planRemaining,
        Long planResetSeconds,
        Long hardLimit,
        Long hardRemaining,
        Long hardResetSeconds,
        Instant observedAt) {
}
