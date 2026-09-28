package cn.org.openbanking.dcc.application.reference;

import java.util.List;

import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.reference.repository.StandardReferenceRepository;
import cn.org.openbanking.dcc.core.standard.reference.ReferenceChecker;

import org.springframework.stereotype.Component;

/**
 * Blocks deleting a data standard that is still referenced by a table structure.
 * Registered automatically; later phases add sibling checkers for interfaces and
 * templates, and the standard service aggregates all of them.
 */
@Component
public class TableReferenceChecker implements ReferenceChecker {

    private final StandardReferenceRepository references;

    public TableReferenceChecker(StandardReferenceRepository references) {
        this.references = references;
    }

    @Override
    public List<String> findReferences(String tenantId, Long standardId) {
        return references.findByTenantIdAndStandardId(tenantId, standardId).stream()
                .filter(reference -> reference.getRefType() == ReferenceType.TABLE_STRUCTURE)
                .map(reference -> "table structure " + reference.getRefObjectCode())
                .distinct()
                .toList();
    }
}
