package com.sorbonne.backend.indexing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookTermRepository extends JpaRepository<BookTerm, Long> {
    List<BookTerm> findByTermIdOrderByOccurrencesDesc(Long termId);
}
