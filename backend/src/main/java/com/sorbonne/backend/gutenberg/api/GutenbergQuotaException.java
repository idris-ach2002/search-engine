package com.sorbonne.backend.gutenberg.api;

public class GutenbergQuotaException extends RuntimeException {
    public GutenbergQuotaException(String message) {
        super(message);
    }
}
