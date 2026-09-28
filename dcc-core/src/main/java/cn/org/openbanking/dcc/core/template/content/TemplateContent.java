package cn.org.openbanking.dcc.core.template.content;

import java.util.List;

/**
 * The versioned content of an interface template: the layered sections (Head split
 * into protocol/gateway/routing/business, plus Body and Trailer) and their fields.
 * Serialised as a JSON snapshot on the version record.
 */
public record TemplateContent(String name, String description, List<TemplateSection> sections) {

    public TemplateContent {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
