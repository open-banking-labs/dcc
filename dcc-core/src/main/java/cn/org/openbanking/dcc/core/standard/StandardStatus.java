package cn.org.openbanking.dcc.core.standard;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

/**
 * Lifecycle of a versioned artifact:
 * 草稿 (draft) → 待审 (pending review) → 发布 (published) → 废弃 (deprecated).
 *
 * <p>Only the transitions in {@link #canTransitionTo} are allowed; anything else
 * raises a {@link ConflictException}.
 */
public enum StandardStatus {

    /** 草稿 - being edited, not yet submitted. */
    DRAFT,

    /** 待审 - submitted and awaiting approval. */
    PENDING_REVIEW,

    /** 发布 - approved and immutable for this version. */
    PUBLISHED,

    /** 废弃 - retired; no further transitions. */
    DEPRECATED;

    private static final Map<StandardStatus, Set<StandardStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(PENDING_REVIEW),
            PENDING_REVIEW, EnumSet.of(PUBLISHED, DRAFT),
            PUBLISHED, EnumSet.of(DEPRECATED),
            DEPRECATED, EnumSet.noneOf(StandardStatus.class));

    public boolean canTransitionTo(StandardStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** Validates the transition or throws {@link ConflictException}. */
    public void requireTransitionTo(StandardStatus target) {
        if (!canTransitionTo(target)) {
            throw new ConflictException("illegal status transition: " + this + " -> " + target);
        }
    }

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
