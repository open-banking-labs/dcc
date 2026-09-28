package cn.org.openbanking.dcc.core.standard.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;

class ContentDifferTest {

    private static final StandardContent BASE = new StandardContent(
            "账号", "desc", "VARCHAR", 32, null, true, null, null, null, "123");

    @Test
    void reportsNoChangeForIdenticalContent() {
        assertThat(ContentDiffer.between(BASE, BASE)).isEmpty();
    }

    @Test
    void reportsChangedFieldWithBeforeAndAfter() {
        StandardContent changed = new StandardContent(
                "账号", "desc", "VARCHAR", 64, null, true, null, null, null, "123");

        List<FieldChange> changes = ContentDiffer.between(BASE, changed);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).field()).isEqualTo("length");
        assertThat(changes.get(0).before()).isEqualTo("32");
        assertThat(changes.get(0).after()).isEqualTo("64");
    }

    @Test
    void reportsMultipleChangesInStableFieldOrder() {
        StandardContent changed = new StandardContent(
                "账号名称", "desc (new)", "VARCHAR", 32, null, true, null, null, null, "123");

        assertThat(ContentDiffer.between(BASE, changed))
                .extracting(FieldChange::field)
                .containsExactly("name", "description");
    }
}
