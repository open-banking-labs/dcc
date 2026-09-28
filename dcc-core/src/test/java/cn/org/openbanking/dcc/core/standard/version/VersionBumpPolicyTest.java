package cn.org.openbanking.dcc.core.standard.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;

class VersionBumpPolicyTest {

    private static final StandardContent BASE = new StandardContent(
            "账号", "account number", "VARCHAR", 32, null, true, null, null, null, "123456");

    @Test
    void descriptionChangeIsPatch() {
        StandardContent changed = withDescription("account number (updated)");
        assertThat(VersionBumpPolicy.determine(BASE, changed).type()).isEqualTo(VersionChangeType.PATCH);
    }

    @Test
    void nameChangeIsMinor() {
        StandardContent changed = withName("账号名称");
        assertThat(VersionBumpPolicy.determine(BASE, changed).type()).isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void constraintChangeIsMajor() {
        StandardContent changed = withLength(64);
        VersionBump bump = VersionBumpPolicy.determine(BASE, changed);
        assertThat(bump.type()).isEqualTo(VersionChangeType.MAJOR);
        assertThat(bump.reasons()).anyMatch(reason -> reason.contains("length"));
    }

    @Test
    void mostSevereChangeWins() {
        StandardContent changed = new StandardContent(
                "账号名称", "account number (updated)", "VARCHAR", 64, null, true, null, null, null, "123456");
        assertThat(VersionBumpPolicy.determine(BASE, changed).type()).isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void noChangeIsRejected() {
        assertThatThrownBy(() -> VersionBumpPolicy.determine(BASE, BASE))
                .isInstanceOf(ValidationException.class);
    }

    private static StandardContent withName(String name) {
        return new StandardContent(name, BASE.description(), BASE.dataType(), BASE.length(), BASE.scale(),
                BASE.required(), BASE.defaultValue(), BASE.enumValues(), BASE.regex(), BASE.exampleValue());
    }

    private static StandardContent withDescription(String description) {
        return new StandardContent(BASE.name(), description, BASE.dataType(), BASE.length(), BASE.scale(),
                BASE.required(), BASE.defaultValue(), BASE.enumValues(), BASE.regex(), BASE.exampleValue());
    }

    private static StandardContent withLength(Integer length) {
        return new StandardContent(BASE.name(), BASE.description(), BASE.dataType(), length, BASE.scale(),
                BASE.required(), BASE.defaultValue(), BASE.enumValues(), BASE.regex(), BASE.exampleValue());
    }
}
