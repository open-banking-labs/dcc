package cn.org.openbanking.dcc.core.standard.version;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.diff.ContentDiffer;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;

/**
 * Decides the semantic-version bump for a data-standard revision from the
 * field-level diff of its two snapshots.
 *
 * <p>Classification follows the project rule (see
 * {@link VersionChangeType}); when several fields change, the most severe class
 * wins:
 * <ul>
 *   <li><b>MAJOR</b> - type or constraint changes ({@code dataType}, {@code length},
 *       {@code scale}, {@code required}, {@code regex}, {@code defaultValue},
 *       {@code enumValues});</li>
 *   <li><b>MINOR</b> - display-name change ({@code name});</li>
 *   <li><b>PATCH</b> - non-structural changes ({@code description},
 *       {@code exampleValue}).</li>
 * </ul>
 */
public final class VersionBumpPolicy {

    private VersionBumpPolicy() {
    }

    public static VersionBump determine(StandardContent before, StandardContent after) {
        List<FieldChange> changes = ContentDiffer.between(before, after);
        if (changes.isEmpty()) {
            throw new ValidationException("no content change: nothing to version");
        }
        VersionChangeType type = VersionChangeType.PATCH;
        List<String> reasons = new ArrayList<>();
        for (FieldChange change : changes) {
            type = VersionChangeType.highest(type, classify(change.field()));
            reasons.add(change.field() + " changed");
        }
        return new VersionBump(type, reasons);
    }

    /** Maps a content field to the class of change it implies. */
    public static VersionChangeType classify(String field) {
        return switch (field) {
            case "dataType", "length", "scale", "required", "regex", "defaultValue", "enumValues" ->
                    VersionChangeType.MAJOR;
            case "name" -> VersionChangeType.MINOR;
            case "description", "exampleValue" -> VersionChangeType.PATCH;
            default -> VersionChangeType.PATCH;
        };
    }
}
