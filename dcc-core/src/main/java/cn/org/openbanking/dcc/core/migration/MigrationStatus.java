package cn.org.openbanking.dcc.core.migration;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

/**
 * Lifecycle of a migration order:
 * 待提交 → 待审批 → 已批准 → 已迁移 (or 已拒绝 / 已回滚).
 */
public enum MigrationStatus {

    /** 待提交 - created, not yet submitted for approval. */
    PENDING_SUBMISSION,
    /** 待审批 - submitted and awaiting approval. */
    PENDING_APPROVAL,
    /** 已批准 - approved, ready to be migrated. */
    APPROVED,
    /** 已迁移 - applied to the target environment. */
    MIGRATED,
    /** 已拒绝 - rejected during approval. */
    REJECTED,
    /** 已回滚 - the migration was rolled back. */
    ROLLED_BACK;

    private static final Map<MigrationStatus, Set<MigrationStatus>> ALLOWED = Map.of(
            PENDING_SUBMISSION, EnumSet.of(PENDING_APPROVAL),
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(MIGRATED),
            MIGRATED, EnumSet.of(ROLLED_BACK),
            REJECTED, EnumSet.noneOf(MigrationStatus.class),
            ROLLED_BACK, EnumSet.noneOf(MigrationStatus.class));

    public boolean canTransitionTo(MigrationStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void requireTransitionTo(MigrationStatus target) {
        if (!canTransitionTo(target)) {
            throw new ConflictException("illegal migration transition: " + this + " -> " + target);
        }
    }
}
