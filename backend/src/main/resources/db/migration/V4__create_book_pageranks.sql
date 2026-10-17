CREATE TABLE book_pageranks (
    book_id BIGINT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_book_pageranks_score CHECK (score >= 0.0)
);

CREATE INDEX idx_book_pageranks_score ON book_pageranks(score DESC);

CREATE TABLE pagerank_state (
    id SMALLINT PRIMARY KEY,
    computed_at TIMESTAMPTZ NOT NULL,
    damping_factor DOUBLE PRECISION NOT NULL,
    tolerance DOUBLE PRECISION NOT NULL,
    max_iterations INTEGER NOT NULL,
    iterations INTEGER NOT NULL,
    converged BOOLEAN NOT NULL,
    final_delta DOUBLE PRECISION NOT NULL,
    node_count INTEGER NOT NULL,
    edge_count INTEGER NOT NULL,
    CONSTRAINT chk_pagerank_state_singleton CHECK (id = 1),
    CONSTRAINT chk_pagerank_state_damping CHECK (damping_factor > 0.0 AND damping_factor < 1.0),
    CONSTRAINT chk_pagerank_state_tolerance CHECK (tolerance > 0.0),
    CONSTRAINT chk_pagerank_state_iterations CHECK (max_iterations > 0 AND iterations >= 0),
    CONSTRAINT chk_pagerank_state_counts CHECK (node_count >= 0 AND edge_count >= 0)
);
