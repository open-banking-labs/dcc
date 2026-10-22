package cn.org.openbanking.dcc.core.id;

import java.time.Instant;

/**
 * Persisted position of one sequence. {@code currentValue} is the high-water mark
 * already reserved from the database; a running instance may still hold a small
 * unissued buffer above it.
 */
public record SequenceSnapshot(
        String key,
        long currentValue,
        long maxValue,
        int step,
        Instant updatedAt) {

    public long remaining() {
        return Math.max(0, maxValue - currentValue);
    }
}
