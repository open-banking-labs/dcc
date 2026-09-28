package cn.org.openbanking.dcc.core.standard.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.org.openbanking.dcc.core.standard.StandardContent;

/**
 * Field-level comparison of two {@link StandardContent} snapshots.
 *
 * <p>This is deliberately a domain comparator, not a generic text diff: the
 * project derives the semantic-version bump from <em>which</em> attributes
 * changed, so the diff must be structural and ordered.
 */
public final class ContentDiffer {

    private ContentDiffer() {
    }

    /** @return the changed fields, in a stable field order; empty when identical. */
    public static List<FieldChange> between(StandardContent before, StandardContent after) {
        List<FieldChange> changes = new ArrayList<>();
        add(changes, "name", before.name(), after.name());
        add(changes, "description", before.description(), after.description());
        add(changes, "dataType", before.dataType(), after.dataType());
        add(changes, "length", before.length(), after.length());
        add(changes, "scale", before.scale(), after.scale());
        add(changes, "required", before.required(), after.required());
        add(changes, "defaultValue", before.defaultValue(), after.defaultValue());
        add(changes, "enumValues", before.enumValues(), after.enumValues());
        add(changes, "regex", before.regex(), after.regex());
        add(changes, "exampleValue", before.exampleValue(), after.exampleValue());
        return changes;
    }

    private static void add(List<FieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, render(before), render(after)));
        }
    }

    private static String render(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
