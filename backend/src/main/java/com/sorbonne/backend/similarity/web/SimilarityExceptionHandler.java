package com.sorbonne.backend.similarity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = SimilarityAdminController.class)
public class SimilarityExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> badRequest(IllegalArgumentException e) { return Map.of("error", e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict(IllegalStateException e) { return Map.of("error", e.getMessage()); }
}
