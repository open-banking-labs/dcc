package cn.org.openbanking.dcc.core.id;

import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed sequence store.
 *
 * <p>{@link #reserve} runs in its own transaction ({@code REQUIRES_NEW}) on purpose:
 * the business transaction may roll back, but an already reserved block must never be
 * handed out again. The cost is a possible gap; the guarantee is no reuse.
 */
@Component
public class DbSequenceStore implements SequenceRangeStore {

    private static final String RESERVE_SQL = """
            WITH next AS (
                INSERT INTO id_sequence (sequence_key, current_value, step, max_value, updated_at)
                VALUES (:key, :step, :step, :maxValue, now())
                ON CONFLICT (sequence_key) DO UPDATE
                SET current_value = id_sequence.current_value + :step,
                    step = :step,
                    max_value = :maxValue,
                    updated_at = now()
                WHERE id_sequence.current_value + :step <= :maxValue
                RETURNING current_value - :step + 1 AS range_start,
                          current_value AS range_end
            )
            SELECT range_start, range_end FROM next
            """;

    private static final String SNAPSHOT_SQL = """
            SELECT sequence_key, current_value, step, max_value, updated_at
            FROM id_sequence
            WHERE sequence_key = :key
            """;

    private static final String SNAPSHOTS_SQL = """
            SELECT sequence_key, current_value, step, max_value, updated_at
            FROM id_sequence
            ORDER BY sequence_key
            """;

    private static final String SNAPSHOTS_PREFIX_SQL = """
            SELECT sequence_key, current_value, step, max_value, updated_at
            FROM id_sequence
            WHERE sequence_key LIKE :pattern ESCAPE '\\'
            ORDER BY sequence_key
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DbSequenceStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SequenceRange reserve(String key, int step, long maxValue) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("step", step)
                .addValue("maxValue", maxValue);
        List<SequenceRange> ranges = jdbc.query(RESERVE_SQL, parameters,
                (rs, rowNum) -> new SequenceRange(rs.getLong("range_start"), rs.getLong("range_end")));
        if (ranges.isEmpty()) {
            throw new ConflictException("identifier sequence exhausted: " + key + " (max " + maxValue + ")");
        }
        return ranges.get(0);
    }

    @Transactional(readOnly = true)
    public SequenceSnapshot snapshot(String key) {
        List<SequenceSnapshot> rows = jdbc.query(SNAPSHOT_SQL,
                new MapSqlParameterSource("key", key), (rs, rowNum) -> mapSnapshot(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<SequenceSnapshot> snapshots(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return jdbc.query(SNAPSHOTS_SQL, new MapSqlParameterSource(), (rs, rowNum) -> mapSnapshot(rs));
        }
        return jdbc.query(SNAPSHOTS_PREFIX_SQL,
                new MapSqlParameterSource("pattern", escapeLike(prefix) + "%"),
                (rs, rowNum) -> mapSnapshot(rs));
    }

    private static SequenceSnapshot mapSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SequenceSnapshot(
                rs.getString("sequence_key"),
                rs.getLong("current_value"),
                rs.getLong("max_value"),
                rs.getInt("step"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
