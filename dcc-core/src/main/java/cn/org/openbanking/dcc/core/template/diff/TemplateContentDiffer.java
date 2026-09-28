package cn.org.openbanking.dcc.core.template.diff;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;
import cn.org.openbanking.dcc.core.template.content.TemplateField;
import cn.org.openbanking.dcc.core.template.content.TemplateSection;
import cn.org.openbanking.dcc.core.template.content.TemplateSectionCode;

/**
 * Structural comparison of two template contents. Sections are matched by code,
 * fields by name; the {@link FieldChange} add/remove convention (null before = added,
 * null after = removed) matches the interface diff.
 */
public final class TemplateContentDiffer {

    private TemplateContentDiffer() {
    }

    public static List<FieldChange> between(TemplateContent before, TemplateContent after) {
        List<FieldChange> changes = new ArrayList<>();
        add(changes, "template.name", before.name(), after.name());
        add(changes, "template.description", before.description(), after.description());

        Map<TemplateSectionCode, TemplateSection> oldByCode = byCode(before.sections());
        Map<TemplateSectionCode, TemplateSection> newByCode = byCode(after.sections());

        for (TemplateSection section : after.sections()) {
            TemplateSection previous = oldByCode.get(section.code());
            String path = "section." + section.code();
            if (previous == null) {
                changes.add(new FieldChange(path, null, section.code().name()));
            } else {
                add(changes, path + ".name", previous.name(), section.name());
                diffFields(changes, path, previous.fields(), section.fields());
            }
        }
        for (TemplateSection section : before.sections()) {
            if (!newByCode.containsKey(section.code())) {
                changes.add(new FieldChange("section." + section.code(), section.code().name(), null));
            }
        }
        return changes;
    }

    private static void diffFields(List<FieldChange> changes, String path,
            List<TemplateField> before, List<TemplateField> after) {
        Map<String, TemplateField> oldByName = byName(before);
        Map<String, TemplateField> newByName = byName(after);

        for (TemplateField field : after) {
            String fieldPath = path + "." + field.name();
            TemplateField previous = oldByName.get(field.name());
            if (previous == null) {
                changes.add(new FieldChange(fieldPath, null, field.name()));
            } else {
                add(changes, fieldPath + ".dataType", previous.dataType(), field.dataType());
                add(changes, fieldPath + ".length", previous.length(), field.length());
                add(changes, fieldPath + ".required", previous.required(), field.required());
                add(changes, fieldPath + ".role", previous.role(), field.role());
                add(changes, fieldPath + ".standard", previous.standardCode(), field.standardCode());
                diffFields(changes, fieldPath, previous.children(), field.children());
            }
        }
        for (TemplateField field : before) {
            if (!newByName.containsKey(field.name())) {
                changes.add(new FieldChange(path + "." + field.name(), field.name(), null));
            }
        }
    }

    private static Map<TemplateSectionCode, TemplateSection> byCode(List<TemplateSection> sections) {
        Map<TemplateSectionCode, TemplateSection> map = new EnumMap<>(TemplateSectionCode.class);
        for (TemplateSection section : sections) {
            map.put(section.code(), section);
        }
        return map;
    }

    private static Map<String, TemplateField> byName(List<TemplateField> fields) {
        Map<String, TemplateField> map = new java.util.LinkedHashMap<>();
        for (TemplateField field : fields) {
            map.put(field.name(), field);
        }
        return map;
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
