package cn.org.openbanking.dcc.core.id;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Leaf-segment style sequence source: the database reserves a block atomically, this
 * process hands out values from that block locally, and a new block is reserved only
 * when the current one is exhausted.
 *
 * <p>Multiple application instances share the same {@code id_sequence} table, so their
 * blocks never overlap. The trade-off is documented and intentional: a restart (or a
 * larger {@code step}) can skip unused values, but a value is never issued twice.
 */
@Component
public class SegmentSequenceSource implements SequenceSource {

    private final SequenceRangeStore store;
    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();

    public SegmentSequenceSource(SequenceRangeStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return SequenceSpec.DB_SEGMENT;
    }

    @Override
    public long next(String key, int step, long maxValue) {
        return buffers.computeIfAbsent(key, ignored -> new SegmentBuffer())
                .next(store, key, step, maxValue);
    }

    @Override
    public SequenceSnapshot snapshot(String key) {
        return store.snapshot(key);
    }

    @Override
    public List<SequenceSnapshot> snapshots(String prefix) {
        return store.snapshots(prefix);
    }
}
