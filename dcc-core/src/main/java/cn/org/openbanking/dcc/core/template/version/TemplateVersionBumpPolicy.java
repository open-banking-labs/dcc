package cn.org.openbanking.dcc.core.template.version;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

/**
 * Semantic-version rule for interface templates. Same policy shape as interfaces:
 * <ul>
 *   <li><b>MAJOR</b> - a section or field removed, a field made required, or a
 *       field's type changed;</li>
 *   <li><b>MINOR</b> - a section/field added, a field made optional, or names/roles
 *       changed;</li>
 *   <li><b>PATCH</b> - description changes.</li>
 * </ul>
 */
public final class TemplateVersionBumpPolicy {

    private TemplateVersionBumpPolicy() {
    }

    public static VersionBump determine(List<FieldChange> changes) {
        if (changes.isEmpty()) {
            throw new ValidationException("no content change: nothing to version");
        }
        VersionChangeType type = VersionChangeType.PATCH;
        List<String> reasons = new ArrayList<>();
        for (FieldChange change : changes) {
            VersionChangeType fieldType = classify(change);
            type = VersionChangeType.highest(type, fieldType);
            reasons.add(change.field() + " (" + fieldType + ")");
        }
        return new VersionBump(type, reasons);
    }

    private static VersionChangeType classify(FieldChange change) {
        if (change.before() == null) {
            return VersionChangeType.MINOR; // added section / field
        }
        if (change.after() == null) {
            return VersionChangeType.MAJOR; // removed section / field
        }
        String field = change.field();
        if (field.endsWith(".required")) {
            return "true".equals(change.after()) ? VersionChangeType.MAJOR : VersionChangeType.MINOR;
        }
        if (field.endsWith(".dataType") || field.endsWith(".length") || field.endsWith(".standard")) {
            return VersionChangeType.MAJOR;
        }
        if (field.equals("template.description")) {
            return VersionChangeType.PATCH;
        }
        return VersionChangeType.MINOR;
    }
}
