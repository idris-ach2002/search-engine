CREATE TABLE book_similarities (
    id BIGSERIAL PRIMARY KEY,
    book_a_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    book_b_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    jaccard_score DOUBLE PRECISION NOT NULL,
    intersection_size INTEGER NOT NULL,
    union_size INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_book_similarities_pair UNIQUE (book_a_id, book_b_id),
    CONSTRAINT chk_book_similarities_order CHECK (book_a_id < book_b_id),
    CONSTRAINT chk_book_similarities_score CHECK (jaccard_score >= 0.0 AND jaccard_score <= 1.0),
    CONSTRAINT chk_book_similarities_intersection CHECK (intersection_size > 0),
    CONSTRAINT chk_book_similarities_union CHECK (union_size >= intersection_size)
);

CREATE INDEX idx_book_similarities_a ON book_similarities(book_a_id);
CREATE INDEX idx_book_similarities_b ON book_similarities(book_b_id);
CREATE INDEX idx_book_similarities_score ON book_similarities(jaccard_score DESC);
