package com.sorbonne.backend.indexing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {
    Optional<Term> findByValue(String value);
}
