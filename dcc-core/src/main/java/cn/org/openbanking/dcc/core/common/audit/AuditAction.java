package cn.org.openbanking.dcc.core.common.audit;

/**
 * The kind of change recorded in the append-only audit trail.
 *
 * <p>{@link #PHYSICAL_DELETE} should never be produced in normal operation — records
 * are retained, not removed — so its appearance is a signal that a hard delete slipped
 * through and is worth alerting on.
 */
public enum AuditAction {

    /** A new row was inserted. */
    CREATE,

    /** An existing row was modified (excluding the retention marker). */
    UPDATE,

    /** A row was logically deleted (retention marker set); the row is kept. */
    LOGICAL_DELETE,

    /** A logically-deleted row was brought back into the default view. */
    RESTORE,

    /** A row was physically removed — retention violation, should not normally happen. */
    PHYSICAL_DELETE
}
