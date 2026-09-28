package cn.org.openbanking.dcc.generator.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;

import org.junit.jupiter.api.Test;

class TableDdlGeneratorTest {

    private final TableDdlGenerator generator = new TableDdlGenerator();

    private static TableContent account() {
        TableColumn acctNo =
                new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 32, null, false, true, null, 1, "账号");
        TableColumn balance =
                new TableColumn("balance", null, null, "NUMERIC", 18, 2, true, false, "0", 2, "余额");
        return new TableContent("Account", "账户表", "acct_no", List.of(acctNo, balance),
                List.of(new TableIndex("ix_account_acct_no", true, List.of("acct_no"))));
    }

    @Test
    void generateCreateEmitsTablePrimaryKeyCommentsAndIndexes() {
        TableDdl ddl = generator.generateCreate("20260902000300", "public", "account", account(), "sha256:abc123");

        assertThat(ddl.dialect()).isEqualTo("postgresql");
        assertThat(ddl.ddl()).contains("CREATE TABLE \"public\".\"account\"");
        assertThat(ddl.ddl()).contains("\"acct_no\" VARCHAR(32) NOT NULL");
        assertThat(ddl.ddl()).contains("\"balance\" NUMERIC(18, 2) DEFAULT 0");
        assertThat(ddl.ddl()).contains("PRIMARY KEY (\"acct_no\")");
        assertThat(ddl.ddl()).contains("COMMENT ON TABLE \"public\".\"account\" IS '账户表'");
        assertThat(ddl.ddl()).contains("COMMENT ON COLUMN \"public\".\"account\".\"acct_no\" IS '账号'");
        assertThat(ddl.ddl()).contains("CREATE UNIQUE INDEX \"ix_account_acct_no\" ON \"public\".\"account\" (\"acct_no\")");
        assertThat(ddl.flywayFileName()).isEqualTo("V20260902000300__create_account.sql");
        assertThat(ddl.flywayScript()).startsWith("-- generated from model@sha256:abc123");
    }

    @Test
    void generateAlterEmitsAddedColumn() {
        TableContent before = new TableContent("Account", "账户表", "acct_no",
                List.of(new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 32, null, false, true, null, 1, "账号")),
                List.of());
        TableContent after = account();

        TableDdl ddl = generator.generateAlter("20260902000310", "public", "account", before, after, "sha256:def456");

        assertThat(ddl.ddl()).contains("ALTER TABLE \"public\".\"account\" ADD COLUMN");
        assertThat(ddl.ddl()).contains("\"balance\" NUMERIC(18, 2) DEFAULT 0");
        assertThat(ddl.flywayFileName()).isEqualTo("V20260902000310__alter_account.sql");
        assertThat(ddl.flywayScript()).startsWith("-- generated from model@sha256:def456");
    }
}
