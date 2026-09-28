package cn.org.openbanking.dcc.core.interfaceapi.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;

import org.junit.jupiter.api.Test;

class InterfaceContentDifferTest {

    private static final InterfaceField ACCTNO =
            new InterfaceField("acctno", 1L, "acctno", "VARCHAR", 32, null, true, false, List.of());
    private static final InterfaceContent BASE = new InterfaceContent(
            "转账", "payment", "/transfer", "转账接口", "transfer", List.of(ACCTNO), List.of());

    @Test
    void detectsAddedField() {
        InterfaceField amount =
                new InterfaceField("amount", null, null, "NUMERIC", 18, 2, true, false, List.of());
        InterfaceContent after = new InterfaceContent("转账", "payment", "/transfer", "转账接口", "transfer",
                List.of(ACCTNO, amount), List.of());

        assertThat(InterfaceContentDiffer.between(BASE, after))
                .anyMatch(change -> change.field().equals("input.amount") && change.before() == null);
    }

    @Test
    void detectsRemovedField() {
        InterfaceContent after = new InterfaceContent("转账", "payment", "/transfer", "转账接口", "transfer",
                List.of(), List.of());

        assertThat(InterfaceContentDiffer.between(BASE, after))
                .anyMatch(change -> change.field().equals("input.acctno") && change.after() == null);
    }

    @Test
    void detectsNestedFieldChange() {
        InterfaceField nestedBefore = new InterfaceField("payload", null, null, null, null, null, true, false,
                List.of(ACCTNO));
        InterfaceField nestedAfter = new InterfaceField("payload", null, null, null, null, null, true, false,
                List.of());

        InterfaceContent before = new InterfaceContent("x", null, null, null, null, List.of(nestedBefore), List.of());
        InterfaceContent after = new InterfaceContent("x", null, null, null, null, List.of(nestedAfter), List.of());

        List<FieldChange> changes = InterfaceContentDiffer.between(before, after);
        assertThat(changes).anyMatch(change -> change.field().equals("input.payload.acctno") && change.after() == null);
    }

    @Test
    void identicalContentHasNoChanges() {
        assertThat(InterfaceContentDiffer.between(BASE, BASE)).isEmpty();
    }
}
