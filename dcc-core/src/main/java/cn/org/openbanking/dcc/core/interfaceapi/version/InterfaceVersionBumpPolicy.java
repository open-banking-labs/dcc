package cn.org.openbanking.dcc.core.interfaceapi.version;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

/**
 * Semantic-version rule for business interfaces:
 * <ul>
 *   <li><b>MAJOR</b> - a field removed, a field made required, or a field's type
 *       changed (breaking for callers);</li>
 *   <li><b>MINOR</b> - a field added, a field made optional, or header attributes
 *       (name/url/...) changed (backward compatible);</li>
 *   <li><b>PATCH</b> - description changes.</li>
 * </ul>
 */
public final class InterfaceVersionBumpPolicy {

    private InterfaceVersionBumpPolicy() {
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
            reasons.add(describe(change) + " (" + fieldType + ")");
        }
        return new VersionBump(type, reasons);
    }

    private static VersionChangeType classify(FieldChange change) {
        if (change.before() == null) {
            return VersionChangeType.MINOR; // added field
        }
        if (change.after() == null) {
            return VersionChangeType.MAJOR; // removed field
        }
        String field = change.field();
        if (field.endsWith(".required")) {
            return "true".equals(change.after()) ? VersionChangeType.MAJOR : VersionChangeType.MINOR;
        }
        if (field.endsWith(".dataType") || field.endsWith(".length") || field.endsWith(".scale")
                || field.endsWith(".list") || field.endsWith(".standard")) {
            return VersionChangeType.MAJOR;
        }
        if (field.equals("interface.description")) {
            return VersionChangeType.PATCH;
        }
        return VersionChangeType.MINOR;
    }

    private static String describe(FieldChange change) {
        if (change.before() == null) {
            return change.field() + " added";
        }
        if (change.after() == null) {
            return change.field() + " removed";
        }
        return change.field() + " changed";
    }
}
