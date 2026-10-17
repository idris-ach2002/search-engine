package com.sorbonne.backend.similarity.service;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class SimilarityStopWords {
    private static final Set<String> WORDS = Set.of(
            "the", "and", "that", "this", "with", "from", "have", "has", "had", "was", "were",
            "are", "been", "for", "not", "but", "you", "your", "his", "her", "she", "him", "they",
            "them", "their", "our", "ours", "who", "what", "when", "where", "which", "would", "could",
            "should", "there", "here", "into", "onto", "than", "then", "too", "very", "can", "all",
            "any", "some", "one", "two", "out", "about", "over", "under", "again", "only", "also"
    );

    public Set<String> values() { return WORDS; }
}
