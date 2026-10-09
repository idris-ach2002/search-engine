package com.sorbonne.backend.indexing;

import com.sorbonne.backend.book.Book;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;

@Entity
@Table(
        name = "book_terms",
        uniqueConstraints = @UniqueConstraint(name = "uq_book_terms_book_term", columnNames = {"book_id", "term_id"}))
public class BookTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(nullable = false)
    private int occurrences;

    protected BookTerm() {
    }

    public BookTerm(Book book, Term term, int occurrences) {
        this.book = book;
        this.term = term;
        this.occurrences = occurrences;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Term getTerm() {
        return term;
    }

    public int getOccurrences() {
        return occurrences;
    }
}
