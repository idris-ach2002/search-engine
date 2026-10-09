CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    gutenberg_id INTEGER NOT NULL UNIQUE,
    title TEXT NOT NULL,
    author TEXT,
    language VARCHAR(32),
    word_count INTEGER,
    local_path TEXT,
    source_url TEXT,
    content_sha256 VARCHAR(64),
    content_bytes BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_books_word_count_non_negative CHECK (word_count IS NULL OR word_count >= 0),
    CONSTRAINT chk_books_content_bytes_non_negative CHECK (content_bytes IS NULL OR content_bytes >= 0)
);

CREATE INDEX idx_books_status ON books(status);
CREATE INDEX idx_books_language ON books(language);
CREATE INDEX idx_books_title_lower ON books((LOWER(title)));

CREATE TABLE terms (
    id BIGSERIAL PRIMARY KEY,
    value TEXT NOT NULL UNIQUE,
    document_frequency INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_terms_document_frequency_non_negative CHECK (document_frequency >= 0)
);

CREATE TABLE book_terms (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    term_id BIGINT NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    occurrences INTEGER NOT NULL,
    CONSTRAINT uq_book_terms_book_term UNIQUE (book_id, term_id),
    CONSTRAINT chk_book_terms_occurrences_positive CHECK (occurrences > 0)
);

CREATE INDEX idx_book_terms_book_id ON book_terms(book_id);
CREATE INDEX idx_book_terms_term_id ON book_terms(term_id);
CREATE INDEX idx_book_terms_term_occurrences ON book_terms(term_id, occurrences DESC);

CREATE TABLE gutenberg_import_checkpoint (
    source VARCHAR(64) PRIMARY KEY,
    page_url TEXT NOT NULL,
    link_index INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_checkpoint_link_index_non_negative CHECK (link_index >= 0)
);
