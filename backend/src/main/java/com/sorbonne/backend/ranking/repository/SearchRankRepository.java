package com.sorbonne.backend.ranking.repository;

import java.util.Collection;
import java.util.Map;

public interface SearchRankRepository {
    boolean hasCurrentScores();
    Map<Long, Double> loadScores(Collection<Long> bookIds);
    double maxScore();
}
