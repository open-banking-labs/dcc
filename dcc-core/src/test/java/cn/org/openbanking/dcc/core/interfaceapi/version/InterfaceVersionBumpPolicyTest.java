package cn.org.openbanking.dcc.core.interfaceapi.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import org.junit.jupiter.api.Test;

class InterfaceVersionBumpPolicyTest {

    @Test
    void addedFieldIsMinor() {
        assertThat(InterfaceVersionBumpPolicy.determine(List.of(new FieldChange("input.amount", null, "amount"))).type())
                .isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void removedFieldIsMajor() {
        assertThat(InterfaceVersionBumpPolicy.determine(List.of(new FieldChange("input.amount", "amount", null))).type())
                .isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void makingFieldRequiredIsMajor() {
        assertThat(InterfaceVersionBumpPolicy
                .determine(List.of(new FieldChange("input.acctno.required", "false", "true"))).type())
                .isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void makingFieldOptionalIsMinor() {
        assertThat(InterfaceVersionBumpPolicy
                .determine(List.of(new FieldChange("input.acctno.required", "true", "false"))).type())
                .isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void typeChangeIsMajor() {
        assertThat(InterfaceVersionBumpPolicy
                .determine(List.of(new FieldChange("output.amount.dataType", "VARCHAR", "NUMERIC"))).type())
                .isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void headerChangeIsMinor() {
        assertThat(InterfaceVersionBumpPolicy
                .determine(List.of(new FieldChange("interface.url", "/a", "/b"))).type())
                .isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void descriptionChangeIsPatch() {
        assertThat(InterfaceVersionBumpPolicy
                .determine(List.of(new FieldChange("interface.description", "a", "b"))).type())
                .isEqualTo(VersionChangeType.PATCH);
    }

    @Test
    void noChangeIsRejected() {
        assertThatThrownBy(() -> InterfaceVersionBumpPolicy.determine(List.of()))
                .isInstanceOf(ValidationException.class);
    }
}
