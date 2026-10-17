package com.sorbonne.backend.similarity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookSimilarityRepository extends JpaRepository<BookSimilarity, Long> {
}
