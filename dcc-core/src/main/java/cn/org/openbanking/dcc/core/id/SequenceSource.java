package cn.org.openbanking.dcc.core.id;

import java.util.List;

/**
 * SPI for the numeric sequence behind an identifier rule.
 *
 * <p>The built-in implementation is {@code db-segment} (Leaf-segment style). A
 * deployment can add another Spring bean (database sequence, Redis, Snowflake, ...)
 * and select it from a rule's {@code sequence.source} without changing the assembly
 * logic.
 */
public interface SequenceSource {

    /** @return the value used by a rule's {@code sequence.source}. */
    String name();

    /**
     * Allocates the next value for {@code key}.
     *
     * @param key      persisted sequence key
     * @param step     how many values to reserve at once ({@code >= 1})
     * @param maxValue inclusive upper bound; exhaustion throws a conflict
     */
    long next(String key, int step, long maxValue);

    SequenceSnapshot snapshot(String key);

    List<SequenceSnapshot> snapshots(String prefix);
}
