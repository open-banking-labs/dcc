package cn.org.openbanking.dcc.core.template.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import org.junit.jupiter.api.Test;

class TemplateVersionBumpPolicyTest {

    @Test
    void addedSectionIsMinor() {
        assertThat(TemplateVersionBumpPolicy
                .determine(List.of(new FieldChange("section.TRAILER", null, "TRAILER"))).type())
                .isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void removedSectionIsMajor() {
        assertThat(TemplateVersionBumpPolicy
                .determine(List.of(new FieldChange("section.TRAILER", "TRAILER", null))).type())
                .isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void addedFieldIsMinor() {
        assertThat(TemplateVersionBumpPolicy
                .determine(List.of(new FieldChange("section.BODY.amount", null, "amount"))).type())
                .isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void makingFieldRequiredIsMajor() {
        assertThat(TemplateVersionBumpPolicy
                .determine(List.of(new FieldChange("section.BODY.acctno.required", "false", "true"))).type())
                .isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void descriptionChangeIsPatch() {
        assertThat(TemplateVersionBumpPolicy
                .determine(List.of(new FieldChange("template.description", "a", "b"))).type())
                .isEqualTo(VersionChangeType.PATCH);
    }

    @Test
    void noChangeIsRejected() {
        assertThatThrownBy(() -> TemplateVersionBumpPolicy.determine(List.of()))
                .isInstanceOf(ValidationException.class);
    }
}
