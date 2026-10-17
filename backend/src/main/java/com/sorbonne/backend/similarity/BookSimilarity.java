package com.sorbonne.backend.similarity;

import com.sorbonne.backend.book.Book;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_similarities", uniqueConstraints = @UniqueConstraint(name = "uq_book_similarities_pair", columnNames = {"book_a_id", "book_b_id"}))
public class BookSimilarity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_a_id", nullable = false)
    private Book bookA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_b_id", nullable = false)
    private Book bookB;

    @Column(name = "jaccard_score", nullable = false)
    private double jaccardScore;

    @Column(name = "intersection_size", nullable = false)
    private int intersectionSize;

    @Column(name = "union_size", nullable = false)
    private int unionSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookSimilarity() {}

    public Long getId() { return id; }
    public Book getBookA() { return bookA; }
    public Book getBookB() { return bookB; }
    public double getJaccardScore() { return jaccardScore; }
    public int getIntersectionSize() { return intersectionSize; }
    public int getUnionSize() { return unionSize; }
    public Instant getCreatedAt() { return createdAt; }
}
