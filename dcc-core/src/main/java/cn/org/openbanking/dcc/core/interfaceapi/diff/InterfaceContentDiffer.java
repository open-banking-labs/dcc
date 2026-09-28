package cn.org.openbanking.dcc.core.interfaceapi.diff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;

/**
 * Recursive structural comparison of two interface contents.
 *
 * <p>Convention on the returned {@link FieldChange}s: an added element has a
 * {@code null} {@code before}, a removed element a {@code null} {@code after}, and an
 * attribute change has both set. This lets the version policy classify add/remove
 * without a separate structure.
 */
public final class InterfaceContentDiffer {

    private InterfaceContentDiffer() {
    }

    public static List<FieldChange> between(InterfaceContent before, InterfaceContent after) {
        List<FieldChange> changes = new ArrayList<>();
        add(changes, "interface.name", before.name(), after.name());
        add(changes, "interface.businessModule", before.businessModule(), after.businessModule());
        add(changes, "interface.url", before.url(), after.url());
        add(changes, "interface.description", before.description(), after.description());
        add(changes, "interface.shortName", before.shortName(), after.shortName());
        diffFields(changes, "input", before.input(), after.input());
        diffFields(changes, "output", before.output(), after.output());
        return changes;
    }

    private static void diffFields(List<FieldChange> changes, String path,
            List<InterfaceField> before, List<InterfaceField> after) {
        Map<String, InterfaceField> oldByName = byName(before);
        Map<String, InterfaceField> newByName = byName(after);

        for (InterfaceField field : after) {
            String fieldPath = path + "." + field.name();
            InterfaceField previous = oldByName.get(field.name());
            if (previous == null) {
                changes.add(new FieldChange(fieldPath, null, field.name()));
            } else {
                add(changes, fieldPath + ".dataType", previous.dataType(), field.dataType());
                add(changes, fieldPath + ".length", previous.length(), field.length());
                add(changes, fieldPath + ".scale", previous.scale(), field.scale());
                add(changes, fieldPath + ".required", previous.required(), field.required());
                add(changes, fieldPath + ".list", previous.list(), field.list());
                add(changes, fieldPath + ".standard", previous.standardCode(), field.standardCode());
                diffFields(changes, fieldPath, previous.children(), field.children());
            }
        }
        for (InterfaceField field : before) {
            if (!newByName.containsKey(field.name())) {
                changes.add(new FieldChange(path + "." + field.name(), field.name(), null));
            }
        }
    }

    private static <T> Map<String, T> byName(List<T> items, java.util.function.Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(key.apply(item), item);
        }
        return map;
    }

    private static Map<String, InterfaceField> byName(List<InterfaceField> fields) {
        return byName(fields, InterfaceField::name);
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
