package cn.org.openbanking.dcc.core.id;

/**
 * Where the numeric part of a rule comes from.
 *
 * <p>{@code key} is a small template resolved against the current context:
 * {@code {type}}, {@code {tenant}} and {@code {application}}. {@code maxValue} caps
 * the sequence so a misconfiguration can never silently widen a segment.
 */
public record SequenceSpec(String source, String key, int step, long maxValue) {

    public static final String DB_SEGMENT = "db-segment";

    public SequenceSpec {
        source = source == null || source.isBlank() ? DB_SEGMENT : source;
        step = step <= 0 ? 1 : step;
    }
}
