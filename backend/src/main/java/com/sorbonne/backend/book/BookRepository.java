package com.sorbonne.backend.book;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByStorageKey(String storageKey);

    long countByStatus(BookStatus status);

    Page<Book> findByStatus(BookStatus status, Pageable pageable);
}
