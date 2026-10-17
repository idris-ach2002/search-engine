package com.sorbonne.backend.ranking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcPageRankRepository implements PageRankRepository, SearchRankRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcPageRankRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<Long> loadReadyBookIds() {
        return jdbc.query(
                "SELECT id FROM books WHERE status='READY' ORDER BY id",
                (rs, rowNum) -> rs.getLong("id")
        );
    }

    @Override
    public List<WeightedEdge> loadSimilarityEdges() {
        return jdbc.query(
                """
                SELECT s.book_a_id, s.book_b_id, s.jaccard_score
                FROM book_similarities s
                JOIN books a ON a.id = s.book_a_id
                JOIN books b ON b.id = s.book_b_id
                WHERE a.status='READY' AND b.status='READY'
                ORDER BY s.book_a_id, s.book_b_id
                """,
                (rs, rowNum) -> new WeightedEdge(
                        rs.getLong("book_a_id"),
                        rs.getLong("book_b_id"),
                        rs.getDouble("jaccard_score")
                )
        );
    }

    @Override
    @Transactional
    public void replaceScores(Map<Long, Double> scores, RunState state) {
        jdbc.update("DELETE FROM book_pageranks");

        if (!scores.isEmpty()) {
            List<Map.Entry<Long, Double>> entries = List.copyOf(scores.entrySet());
            jdbc.batchUpdate(
                    "INSERT INTO book_pageranks(book_id, score, computed_at) VALUES (?,?,?)",
                    entries,
                    500,
                    (ps, entry) -> {
                        ps.setLong(1, entry.getKey());
                        ps.setDouble(2, entry.getValue());
                        ps.setTimestamp(3, Timestamp.from(state.computedAt()));
                    }
            );
        }

        jdbc.update(
                """
                INSERT INTO pagerank_state(
                    id, computed_at, damping_factor, tolerance, max_iterations,
                    iterations, converged, final_delta, node_count, edge_count
                ) VALUES (1,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                    computed_at=EXCLUDED.computed_at,
                    damping_factor=EXCLUDED.damping_factor,
                    tolerance=EXCLUDED.tolerance,
                    max_iterations=EXCLUDED.max_iterations,
                    iterations=EXCLUDED.iterations,
                    converged=EXCLUDED.converged,
                    final_delta=EXCLUDED.final_delta,
                    node_count=EXCLUDED.node_count,
                    edge_count=EXCLUDED.edge_count
                """,
                Timestamp.from(state.computedAt()),
                state.dampingFactor(),
                state.tolerance(),
                state.maxIterations(),
                state.iterations(),
                state.converged(),
                state.finalDelta(),
                state.nodeCount(),
                state.edgeCount()
        );
    }

    @Override
    public long countScores() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM book_pageranks", Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public long countSimilarityEdges() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM book_similarities", Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public Optional<RunState> loadState() {
        List<RunState> states = jdbc.query(
                """
                SELECT computed_at, damping_factor, tolerance, max_iterations,
                       iterations, converged, final_delta, node_count, edge_count
                FROM pagerank_state
                WHERE id=1
                """,
                (rs, rowNum) -> new RunState(
                        rs.getTimestamp("computed_at").toInstant(),
                        rs.getDouble("damping_factor"),
                        rs.getDouble("tolerance"),
                        rs.getInt("max_iterations"),
                        rs.getInt("iterations"),
                        rs.getBoolean("converged"),
                        rs.getDouble("final_delta"),
                        rs.getInt("node_count"),
                        rs.getInt("edge_count")
                )
        );
        return states.stream().findFirst();
    }

    @Override
    public Instant lastSimilarityBuiltAt() {
        Timestamp value = jdbc.queryForObject(
                "SELECT MAX(created_at) FROM book_similarities",
                Timestamp.class
        );
        return value == null ? null : value.toInstant();
    }

    @Override
    public List<RankedBook> topScores(int limit) {
        return jdbc.query(
                """
                SELECT b.id, b.gutenberg_id, b.title, b.author, pr.score
                FROM book_pageranks pr
                JOIN books b ON b.id = pr.book_id
                WHERE b.status='READY'
                ORDER BY pr.score DESC, b.id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new RankedBook(
                        rs.getLong("id"),
                        rs.getInt("gutenberg_id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getDouble("score")
                ),
                limit
        );
    }

    @Override
    public boolean hasCurrentScores() {
        Optional<RunState> state = loadState();
        if (state.isEmpty()) {
            return false;
        }

        Long readyBooks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books WHERE status='READY'",
                Long.class
        );
        long ready = readyBooks == null ? 0 : readyBooks;
        if (countScores() != ready || state.get().nodeCount() != ready) {
            return false;
        }

        Instant similarityBuiltAt = lastSimilarityBuiltAt();
        return similarityBuiltAt == null || !state.get().computedAt().isBefore(similarityBuiltAt);
    }

    @Override
    public Map<Long, Double> loadScores(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource("bookIds", bookIds);
        Map<Long, Double> result = new HashMap<>();
        named.query(
                "SELECT book_id, score FROM book_pageranks WHERE book_id IN (:bookIds)",
                parameters,
                (RowCallbackHandler) rs -> {
                    result.put(rs.getLong("book_id"), rs.getDouble("score"));
                }
        );
        return Map.copyOf(result);
    }

    @Override
    public double maxScore() {
        Double value = jdbc.queryForObject("SELECT MAX(score) FROM book_pageranks", Double.class);
        return value == null ? 0.0 : value;
    }
}
