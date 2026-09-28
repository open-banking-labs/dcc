package cn.org.openbanking.dcc.core.template.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.template.content.TemplateContent;
import cn.org.openbanking.dcc.core.template.content.TemplateField;
import cn.org.openbanking.dcc.core.template.content.TemplateSection;
import cn.org.openbanking.dcc.core.template.content.TemplateSectionCode;

import org.junit.jupiter.api.Test;

class TemplateContentDifferTest {

    private static final TemplateField GATEWAY =
            new TemplateField("gwId", null, null, "VARCHAR", 16, null, true, "GATEWAY", List.of());
    private static final TemplateContent BASE = new TemplateContent("标准模板", "desc", List.of(
            new TemplateSection(TemplateSectionCode.HEAD_GATEWAY, "网关头", List.of(GATEWAY)),
            new TemplateSection(TemplateSectionCode.BODY, "数据体", List.of())));

    @Test
    void detectsAddedSection() {
        TemplateContent after = new TemplateContent("标准模板", "desc", List.of(
                new TemplateSection(TemplateSectionCode.HEAD_GATEWAY, "网关头", List.of(GATEWAY)),
                new TemplateSection(TemplateSectionCode.BODY, "数据体", List.of()),
                new TemplateSection(TemplateSectionCode.TRAILER, "尾部", List.of())));

        assertThat(TemplateContentDiffer.between(BASE, after))
                .anyMatch(change -> change.field().equals("section.TRAILER") && change.before() == null);
    }

    @Test
    void detectsAddedBodyField() {
        TemplateField amount =
                new TemplateField("amount", null, null, "NUMERIC", 18, 2, true, "BODY", List.of());
        TemplateContent after = new TemplateContent("标准模板", "desc", List.of(
                new TemplateSection(TemplateSectionCode.HEAD_GATEWAY, "网关头", List.of(GATEWAY)),
                new TemplateSection(TemplateSectionCode.BODY, "数据体", List.of(amount))));

        assertThat(TemplateContentDiffer.between(BASE, after))
                .anyMatch(change -> change.field().equals("section.BODY.amount") && change.before() == null);
    }

    @Test
    void identicalContentHasNoChanges() {
        assertThat(TemplateContentDiffer.between(BASE, BASE)).isEmpty();
    }
}
