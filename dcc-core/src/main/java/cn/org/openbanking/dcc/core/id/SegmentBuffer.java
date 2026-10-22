package cn.org.openbanking.dcc.core.id;

/**
 * In-memory buffer for a database-backed number segment.
 *
 * <p>Reservation is the only shared operation; issuing values from the already
 * reserved range is a local increment. A restart drops the buffer and reserves a new
 * range, so numbers left in an old range are skipped and never reused.
 */
final class SegmentBuffer {

    private long next = 1;
    private long end;

    synchronized long next(SequenceRangeStore store, String key, int step, long maxValue) {
        if (next > end) {
            SequenceRange range = store.reserve(key, step, maxValue);
            next = range.start();
            end = range.end();
        }
        return next++;
    }
}
