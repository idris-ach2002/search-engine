package com.sorbonne.backend.gutenberg.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sorbonne.backend.gutenberg.api.GutenbergQuotaException;

@RestControllerAdvice
public class GutenbergExceptionHandler {

    @ExceptionHandler(GutenbergQuotaException.class)
    public ResponseEntity<Map<String, Object>> quotaExceeded(GutenbergQuotaException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(error("RAPIDAPI_QUOTA_GUARD", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(error("BAD_REQUEST", exception.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString());
    }
}
