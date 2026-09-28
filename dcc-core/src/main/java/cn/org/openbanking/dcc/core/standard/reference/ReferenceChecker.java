package cn.org.openbanking.dcc.core.standard.reference;

import java.util.List;

/**
 * Extension point for "can this data standard be deleted?" checks.
 *
 * <p>Later phases (table structures, interfaces, templates) register their own
 * {@code ReferenceChecker} beans; the standard service aggregates all of them, so
 * deleting a standard that is still referenced is rejected. No implementations
 * exist yet in this slice, so the aggregate is empty and deletion always passes.
 */
public interface ReferenceChecker {

    /**
     * @param tenantId   the tenant scope to search within
     * @param standardId the data standard being checked
     * @return human-readable descriptions of references (empty when none); never
     *         {@code null}
     */
    List<String> findReferences(String tenantId, Long standardId);

    /** Ordering hint when several checkers exist; higher runs later. */
    default int order() {
        return 0;
    }
}
