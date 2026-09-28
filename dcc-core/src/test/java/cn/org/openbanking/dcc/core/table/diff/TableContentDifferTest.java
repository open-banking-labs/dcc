package cn.org.openbanking.dcc.core.table.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;

import org.junit.jupiter.api.Test;

class TableContentDifferTest {

    private static final TableColumn ACCT_NO =
            new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 32, null, false, true, null, 1, "账号");
    private static final TableContent BASE =
            new TableContent("Account", "desc", "acct_no", List.of(ACCT_NO), List.of());

    @Test
    void detectsAddedColumn() {
        TableColumn name = new TableColumn("name", null, null, "VARCHAR", 64, null, true, false, null, 2, null);
        TableContent after = new TableContent("Account", "desc", "acct_no", List.of(ACCT_NO, name), List.of());

        TableChangeSet changes = TableContentDiffer.between(BASE, after);

        assertThat(changes.columns()).hasSize(1);
        assertThat(changes.columns().get(0).columnName()).isEqualTo("name");
        assertThat(changes.columns().get(0).kind()).isEqualTo(ChangeKind.ADDED);
    }

    @Test
    void detectsRemovedColumn() {
        TableContent after = new TableContent("Account", "desc", "acct_no", List.of(), List.of());

        TableChangeSet changes = TableContentDiffer.between(BASE, after);

        assertThat(changes.columns()).hasSize(1);
        assertThat(changes.columns().get(0).kind()).isEqualTo(ChangeKind.REMOVED);
    }

    @Test
    void detectsModifiedColumnAttributes() {
        TableColumn widened =
                new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 64, null, false, true, null, 1, "账号");
        TableContent after = new TableContent("Account", "desc", "acct_no", List.of(widened), List.of());

        TableChangeSet changes = TableContentDiffer.between(BASE, after);

        assertThat(changes.columns()).hasSize(1);
        assertThat(changes.columns().get(0).kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(changes.columns().get(0).fields()).extracting("field").contains("length");
    }

    @Test
    void identicalContentHasNoChanges() {
        assertThat(TableContentDiffer.between(BASE, BASE).isEmpty()).isTrue();
    }

    @Test
    void detectsShardKeyChange() {
        TableContent after = new TableContent("Account", "desc", "id", List.of(ACCT_NO), List.of());
        assertThat(TableContentDiffer.between(BASE, after).tableFields())
                .extracting("field").contains("table.shardKey");
    }
}
