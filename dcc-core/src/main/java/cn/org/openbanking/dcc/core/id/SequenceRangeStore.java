package cn.org.openbanking.dcc.core.id;

import java.util.List;

/**
 * Storage side of the segment source. Kept as an interface so the in-memory buffer can
 * be tested without PostgreSQL and so another store (Redis, a database sequence, ...)
 * can be plugged in behind the same {@link SequenceSource} contract.
 */
public interface SequenceRangeStore {

    SequenceRange reserve(String key, int step, long maxValue);

    SequenceSnapshot snapshot(String key);

    List<SequenceSnapshot> snapshots(String prefix);
}
