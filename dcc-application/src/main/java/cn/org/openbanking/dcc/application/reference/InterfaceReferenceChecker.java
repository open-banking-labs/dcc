package cn.org.openbanking.dcc.application.reference;

import java.util.List;

import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.reference.repository.StandardReferenceRepository;
import cn.org.openbanking.dcc.core.standard.reference.ReferenceChecker;

import org.springframework.stereotype.Component;

/** Blocks deleting a data standard that is still referenced by a business interface. */
@Component
public class InterfaceReferenceChecker implements ReferenceChecker {

    private final StandardReferenceRepository references;

    public InterfaceReferenceChecker(StandardReferenceRepository references) {
        this.references = references;
    }

    @Override
    public List<String> findReferences(String tenantId, Long standardId) {
        return references.findByTenantIdAndStandardId(tenantId, standardId).stream()
                .filter(reference -> reference.getRefType() == ReferenceType.INTERFACE)
                .map(reference -> "business interface " + reference.getRefObjectCode())
                .distinct()
                .toList();
    }
}
