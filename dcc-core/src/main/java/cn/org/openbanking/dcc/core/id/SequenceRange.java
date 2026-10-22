package cn.org.openbanking.dcc.core.id;

/** Inclusive range of sequence values reserved for one application instance. */
public record SequenceRange(long start, long end) {
}
