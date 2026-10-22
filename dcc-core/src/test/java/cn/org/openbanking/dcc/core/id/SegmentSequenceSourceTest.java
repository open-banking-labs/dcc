package cn.org.openbanking.dcc.core.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

import org.junit.jupiter.api.Test;

class SegmentSequenceSourceTest {

    @Test
    void reservesBlocksAndNeverReusesAfterARestart() {
        FakeStore store = new FakeStore();
        SegmentSequenceSource first = new SegmentSequenceSource(store);

        assertThat(first.next("artifact:S:1:2", 3, 999)).isEqualTo(1);
        assertThat(first.next("artifact:S:1:2", 3, 999)).isEqualTo(2);
        assertThat(first.next("artifact:S:1:2", 3, 999)).isEqualTo(3);

        // Simulate an application restart: the old buffer (4..6 reserved on the first
        // call above only when needed) is dropped; the new instance reserves 4..6 and
        // starts at 4, while numbers 5 and 6 remain unused but are never reissued as 1..3.
        SegmentSequenceSource restarted = new SegmentSequenceSource(store);
        long afterRestart = restarted.next("artifact:S:1:2", 3, 999);
        assertThat(afterRestart).isGreaterThanOrEqualTo(4);
    }

    @Test
    void concurrentCallersNeverSeeTheSameValue() throws Exception {
        SegmentSequenceSource source = new SegmentSequenceSource(new FakeStore());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Set<Long> values = ConcurrentHashMap.newKeySet();
        try {
            for (int i = 0; i < 200; i++) {
                pool.submit(() -> values.add(source.next("artifact:I:1:2", 10, 999_999)));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(values).hasSize(200);
    }

    @Test
    void refusesToGrowPastTheConfiguredMaximum() {
        SegmentSequenceSource source = new SegmentSequenceSource(new FakeStore());

        assertThat(source.next("tenant-code", 2, 2)).isEqualTo(1);
        assertThat(source.next("tenant-code", 2, 2)).isEqualTo(2);
        assertThatThrownBy(() -> source.next("tenant-code", 2, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    void snapshotReflectsThePersistedHighWaterMark() {
        FakeStore store = new FakeStore();
        SegmentSequenceSource source = new SegmentSequenceSource(store);
        source.next("tenant-code", 5, 999);

        assertThat(store.snapshot("tenant-code").currentValue()).isEqualTo(5);
        assertThat(source.snapshot("tenant-code").remaining()).isEqualTo(994);
    }

    private static final class FakeStore implements SequenceRangeStore {

        private final Map<String, AtomicLong> positions = new ConcurrentHashMap<>();

        @Override
        public SequenceRange reserve(String key, int step, long maxValue) {
            AtomicLong position = positions.computeIfAbsent(key, ignored -> new AtomicLong());
            long end = position.addAndGet(step);
            if (end > maxValue) {
                position.addAndGet(-step);
                throw new ConflictException("identifier sequence exhausted: " + key);
            }
            return new SequenceRange(end - step + 1, end);
        }

        @Override
        public SequenceSnapshot snapshot(String key) {
            long current = positions.getOrDefault(key, new AtomicLong()).get();
            return new SequenceSnapshot(key, current, 999, 1, Instant.EPOCH);
        }

        @Override
        public List<SequenceSnapshot> snapshots(String prefix) {
            return List.of();
        }
    }
}
