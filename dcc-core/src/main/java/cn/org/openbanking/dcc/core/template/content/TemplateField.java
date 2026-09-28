package cn.org.openbanking.dcc.core.template.content;

import java.util.List;

/**
 * A field of a template section. It may reference a data standard or be inline, and
 * carries the owning role; nested structures use {@link #children}.
 */
public record TemplateField(
        String name,
        Long standardId,
        String standardCode,
        String dataType,
        Integer length,
        Integer scale,
        boolean required,
        String role,
        List<TemplateField> children) {

    public TemplateField {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
