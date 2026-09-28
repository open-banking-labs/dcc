package cn.org.openbanking.dcc.core.standard.diff;

/**
 * A single changed attribute between two snapshots. Values are rendered as text
 * ({@code null} when absent) so the diff is transport-ready and stable across
 * numeric/boolean/string fields.
 */
public record FieldChange(String field, String before, String after) {
}
