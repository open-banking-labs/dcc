package cn.org.openbanking.dcc.core.template.content;

import java.util.List;

/** One layer of a template: its code, display name and fields. */
public record TemplateSection(TemplateSectionCode code, String name, List<TemplateField> fields) {

    public TemplateSection {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
