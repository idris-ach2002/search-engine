package com.sorbonne.backend.similarity.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcSimilarityGraphRepository implements SimilarityGraphRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcSimilarityGraphRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override public long countReadyBooks() {
        Long v = jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE status='READY'", Long.class);
        return v == null ? 0 : v;
    }

    @Override public long countIndexedReadyBooks() {
        Long v = jdbc.queryForObject("SELECT COUNT(DISTINCT b.id) FROM books b JOIN book_terms bt ON bt.book_id=b.id WHERE b.status='READY'", Long.class);
        return v == null ? 0 : v;
    }

    private MapSqlParameterSource params(int minDf, int maxDf, int minLength, Collection<String> excluded) {
        return new MapSqlParameterSource().addValue("minDf", minDf).addValue("maxDf", maxDf)
                .addValue("minLength", minLength).addValue("excluded", excluded);
    }

    private String eligibleWhere() {
        return "t.document_frequency BETWEEN :minDf AND :maxDf AND char_length(t.value) >= :minLength AND t.value NOT IN (:excluded)";
    }

    @Override public long countEligibleTerms(int minDf, int maxDf, int minLength, Collection<String> excluded) {
        Long v = named.queryForObject("SELECT COUNT(*) FROM terms t WHERE " + eligibleWhere(), params(minDf,maxDf,minLength,excluded), Long.class);
        return v == null ? 0 : v;
    }

    @Override public long countEligibleFeaturePostings(int minDf, int maxDf, int minLength, Collection<String> excluded) {
        String sql = "SELECT COUNT(*) FROM book_terms bt JOIN terms t ON t.id=bt.term_id JOIN books b ON b.id=bt.book_id WHERE b.status='READY' AND " + eligibleWhere();
        Long v = named.queryForObject(sql, params(minDf,maxDf,minLength,excluded), Long.class);
        return v == null ? 0 : v;
    }

    @Override public Map<Long, Integer> loadFeatureCounts(int minDf, int maxDf, int minLength, Collection<String> excluded) {
        String sql = "SELECT bt.book_id, COUNT(*) AS feature_count FROM book_terms bt JOIN terms t ON t.id=bt.term_id JOIN books b ON b.id=bt.book_id WHERE b.status='READY' AND " + eligibleWhere() + " GROUP BY bt.book_id";
        Map<Long,Integer> result = new HashMap<>();
        named.query(sql, params(minDf,maxDf,minLength,excluded), (RowCallbackHandler) rs -> { result.put(rs.getLong("book_id"), rs.getInt("feature_count")); });
        return Map.copyOf(result);
    }

    @Override public List<PairIntersection> loadPairIntersections(int minDf, int maxDf, int minLength, int minimumSharedTerms, Collection<String> excluded) {
        String sql = """
            SELECT a.book_id AS book_a_id, b.book_id AS book_b_id, COUNT(*) AS intersection_size
            FROM book_terms a
            JOIN book_terms b ON b.term_id=a.term_id AND a.book_id < b.book_id
            JOIN terms t ON t.id=a.term_id
            JOIN books ba ON ba.id=a.book_id
            JOIN books bb ON bb.id=b.book_id
            WHERE ba.status='READY' AND bb.status='READY'
              AND t.document_frequency BETWEEN :minDf AND :maxDf
              AND char_length(t.value) >= :minLength
              AND t.value NOT IN (:excluded)
            GROUP BY a.book_id, b.book_id
            HAVING COUNT(*) >= :minimumSharedTerms
            """;
        MapSqlParameterSource p = params(minDf,maxDf,minLength,excluded).addValue("minimumSharedTerms", minimumSharedTerms);
        return named.query(sql, p, (rs,n) -> new PairIntersection(rs.getLong("book_a_id"), rs.getLong("book_b_id"), rs.getInt("intersection_size")));
    }

    @Override @Transactional
    public void replaceEdges(List<SimilarityEdge> edges) {
        jdbc.update("DELETE FROM book_similarities");
        if (edges.isEmpty()) return;
        jdbc.batchUpdate("INSERT INTO book_similarities(book_a_id,book_b_id,jaccard_score,intersection_size,union_size) VALUES (?,?,?,?,?)",
                edges, 500, (ps,e) -> { ps.setLong(1,e.bookAId()); ps.setLong(2,e.bookBId()); ps.setDouble(3,e.score()); ps.setInt(4,e.intersectionSize()); ps.setInt(5,e.unionSize()); });
    }

    @Override public long countEdges() {
        Long v = jdbc.queryForObject("SELECT COUNT(*) FROM book_similarities", Long.class);
        return v == null ? 0 : v;
    }

    @Override public Instant lastBuiltAt() {
        Timestamp t = jdbc.queryForObject("SELECT MAX(created_at) FROM book_similarities", Timestamp.class);
        return t == null ? null : t.toInstant();
    }

    @Override public List<Neighbor> findNeighbors(long bookId, int limit) {
        String sql = """
            SELECT CASE WHEN s.book_a_id=? THEN bb.id ELSE ba.id END AS neighbor_id,
                   CASE WHEN s.book_a_id=? THEN bb.gutenberg_id ELSE ba.gutenberg_id END AS gutenberg_id,
                   CASE WHEN s.book_a_id=? THEN bb.title ELSE ba.title END AS title,
                   CASE WHEN s.book_a_id=? THEN bb.author ELSE ba.author END AS author,
                   s.jaccard_score, s.intersection_size, s.union_size
            FROM book_similarities s
            JOIN books ba ON ba.id=s.book_a_id
            JOIN books bb ON bb.id=s.book_b_id
            WHERE s.book_a_id=? OR s.book_b_id=?
            ORDER BY s.jaccard_score DESC, neighbor_id ASC
            LIMIT ?
            """;
        return jdbc.query(sql, (rs,n) -> new Neighbor(rs.getLong("neighbor_id"), rs.getInt("gutenberg_id"), rs.getString("title"), rs.getString("author"), rs.getDouble("jaccard_score"), rs.getInt("intersection_size"), rs.getInt("union_size")),
                bookId,bookId,bookId,bookId,bookId,bookId,limit);
    }
}
