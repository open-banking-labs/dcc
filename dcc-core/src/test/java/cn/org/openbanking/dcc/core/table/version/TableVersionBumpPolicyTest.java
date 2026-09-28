package cn.org.openbanking.dcc.core.table.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.table.diff.ChangeKind;
import cn.org.openbanking.dcc.core.table.diff.ColumnChange;
import cn.org.openbanking.dcc.core.table.diff.TableChangeSet;

import org.junit.jupiter.api.Test;

class TableVersionBumpPolicyTest {

    @Test
    void addedColumnIsMinor() {
        TableChangeSet changes = new TableChangeSet(
                List.of(new ColumnChange("name", ChangeKind.ADDED, List.of())), List.of(), List.of());
        assertThat(TableVersionBumpPolicy.determine(changes).type()).isEqualTo(VersionChangeType.MINOR);
    }

    @Test
    void removedColumnIsMajor() {
        TableChangeSet changes = new TableChangeSet(
                List.of(new ColumnChange("name", ChangeKind.REMOVED, List.of())), List.of(), List.of());
        assertThat(TableVersionBumpPolicy.determine(changes).type()).isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void typeChangeIsMajor() {
        TableChangeSet changes = new TableChangeSet(
                List.of(new ColumnChange("acct_no", ChangeKind.MODIFIED,
                        List.of(new FieldChange("dataType", "VARCHAR", "NUMERIC")))),
                List.of(), List.of());
        assertThat(TableVersionBumpPolicy.determine(changes).type()).isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void commentChangeIsPatch() {
        TableChangeSet changes = new TableChangeSet(
                List.of(new ColumnChange("acct_no", ChangeKind.MODIFIED,
                        List.of(new FieldChange("comment", "old", "new")))),
                List.of(), List.of());
        assertThat(TableVersionBumpPolicy.determine(changes).type()).isEqualTo(VersionChangeType.PATCH);
    }

    @Test
    void shardKeyChangeIsMajor() {
        TableChangeSet changes = new TableChangeSet(List.of(),
                List.of(new FieldChange("table.shardKey", "acct_no", "id")), List.of());
        assertThat(TableVersionBumpPolicy.determine(changes).type()).isEqualTo(VersionChangeType.MAJOR);
    }

    @Test
    void noChangeIsRejected() {
        assertThatThrownBy(() -> TableVersionBumpPolicy.determine(new TableChangeSet(List.of(), List.of(), List.of())))
                .isInstanceOf(ValidationException.class);
    }
}
