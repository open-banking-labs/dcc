package cn.org.openbanking.dcc.core.standard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

import org.junit.jupiter.api.Test;

class StandardStatusTest {

    @Test
    void allowsTheDocumentedForwardFlow() {
        assertThat(StandardStatus.DRAFT.canTransitionTo(StandardStatus.PENDING_REVIEW)).isTrue();
        assertThat(StandardStatus.PENDING_REVIEW.canTransitionTo(StandardStatus.PUBLISHED)).isTrue();
        assertThat(StandardStatus.PENDING_REVIEW.canTransitionTo(StandardStatus.DRAFT)).isTrue();
        assertThat(StandardStatus.PUBLISHED.canTransitionTo(StandardStatus.DEPRECATED)).isTrue();
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThat(StandardStatus.DRAFT.canTransitionTo(StandardStatus.PUBLISHED)).isFalse();
        assertThat(StandardStatus.PUBLISHED.canTransitionTo(StandardStatus.DRAFT)).isFalse();
        assertThat(StandardStatus.DEPRECATED.canTransitionTo(StandardStatus.DRAFT)).isFalse();
    }

    @Test
    void requireTransitionToThrowsOnIllegalMove() {
        assertThatThrownBy(() -> StandardStatus.DRAFT.requireTransitionTo(StandardStatus.PUBLISHED))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requireTransitionToAcceptsLegalMove() {
        StandardStatus.DRAFT.requireTransitionTo(StandardStatus.PENDING_REVIEW);
    }
}
