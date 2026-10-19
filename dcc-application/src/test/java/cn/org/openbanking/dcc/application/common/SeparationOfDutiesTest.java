package cn.org.openbanking.dcc.application.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.org.openbanking.dcc.application.tenant.TenantProperties;
import cn.org.openbanking.dcc.core.common.error.ConflictException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Four-eyes principle (ADR 0005 §D4): the author of a change cannot approve it. */
class SeparationOfDutiesTest {

    private final ChangeContext changeContext = new ChangeContext(new TenantProperties());
    private final SeparationOfDuties sod = new SeparationOfDuties(changeContext);

    @AfterEach
    void clear() {
        changeContext.clear();
    }

    @Test
    void submitterCannotApproveItsOwnChange() {
        changeContext.set("alice", "submit");

        assertThatThrownBy(() -> sod.requireFourEyes("alice", "migration order 7"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("four-eyes");
    }

    @Test
    void aDifferentSubjectMayApprove() {
        changeContext.set("bob", "approve");

        assertThatCode(() -> sod.requireFourEyes("alice", "migration order 7")).doesNotThrowAnyException();
    }

    @Test
    void unknownSubmitterDoesNotBlockApproval() {
        changeContext.set("bob", "approve");

        assertThatCode(() -> sod.requireFourEyes(null, "migration order 7")).doesNotThrowAnyException();
        assertThatCode(() -> sod.requireFourEyes("  ", "migration order 7")).doesNotThrowAnyException();
    }
}
