package cn.org.openbanking.dcc.core.standard.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.org.openbanking.dcc.core.common.error.ValidationException;

import org.junit.jupiter.api.Test;

class SemVerTest {

    @Test
    void parsesValidVersion() {
        assertThat(SemVer.parse("1.2.3")).isEqualTo(new SemVer(1, 2, 3));
    }

    @Test
    void rejectsMalformedVersion() {
        assertThatThrownBy(() -> SemVer.parse("1.2")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> SemVer.parse("x.y.z")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> SemVer.parse(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void bumpsByChangeType() {
        assertThat(new SemVer(1, 2, 3).bump(VersionChangeType.MAJOR)).isEqualTo(new SemVer(2, 0, 0));
        assertThat(new SemVer(1, 2, 3).bump(VersionChangeType.MINOR)).isEqualTo(new SemVer(1, 3, 0));
        assertThat(new SemVer(1, 2, 3).bump(VersionChangeType.PATCH)).isEqualTo(new SemVer(1, 2, 4));
    }

    @Test
    void ordersByMajorThenMinorThenPatch() {
        assertThat(SemVer.parse("1.2.3")).isLessThan(SemVer.parse("1.3.0"));
        assertThat(SemVer.parse("1.3.0")).isLessThan(SemVer.parse("2.0.0"));
        assertThat(SemVer.parse("2.0.0")).isGreaterThan(SemVer.parse("1.9.9"));
        assertThat(SemVer.parse("1.0.0")).isEqualByComparingTo(SemVer.parse("1.0.0"));
    }

    @Test
    void rendersCanonicalText() {
        assertThat(new SemVer(1, 2, 3).value()).isEqualTo("1.2.3");
        assertThat(SemVer.INITIAL.value()).isEqualTo("1.0.0");
    }
}
