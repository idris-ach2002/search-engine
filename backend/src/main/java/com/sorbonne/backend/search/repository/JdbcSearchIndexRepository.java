package com.sorbonne.backend.search.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcSearchIndexRepository implements SearchIndexRepository {

    private static final int QUERY_CHUNK_SIZE = 750;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcSearchIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public List<IndexedTerm> loadTerms() {
        return jdbcTemplate.query(
                "SELECT id, value FROM terms ORDER BY id",
                (rs, rowNum) -> new IndexedTerm(rs.getLong("id"), rs.getString("value"))
        );
    }

    @Override
    public List<BookPosting> loadReadyBookPostings(List<Long> termIds) {
        if (termIds == null || termIds.isEmpty()) {
            return List.of();
        }

        List<BookPosting> postings = new ArrayList<>();
        for (int start = 0; start < termIds.size(); start += QUERY_CHUNK_SIZE) {
            int end = Math.min(termIds.size(), start + QUERY_CHUNK_SIZE);
            List<Long> chunk = termIds.subList(start, end);
            MapSqlParameterSource parameters = new MapSqlParameterSource("termIds", chunk);

            postings.addAll(namedJdbcTemplate.query(
                    """
                    SELECT
                        b.id AS book_id,
                        b.gutenberg_id,
                        b.title,
                        b.author,
                        b.language,
                        b.word_count,
                        bt.term_id,
                        bt.occurrences
                    FROM book_terms bt
                    JOIN books b ON b.id = bt.book_id
                    WHERE bt.term_id IN (:termIds)
                      AND b.status = 'READY'
                    """,
                    parameters,
                    (rs, rowNum) -> mapPosting(rs)
            ));
        }
        return List.copyOf(postings);
    }

    private static BookPosting mapPosting(ResultSet rs) throws SQLException {
        int wordCount = rs.getInt("word_count");
        Integer nullableWordCount = rs.wasNull() ? null : wordCount;

        return new BookPosting(
                rs.getLong("book_id"),
                rs.getInt("gutenberg_id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("language"),
                nullableWordCount,
                rs.getLong("term_id"),
                rs.getInt("occurrences")
        );
    }
}
