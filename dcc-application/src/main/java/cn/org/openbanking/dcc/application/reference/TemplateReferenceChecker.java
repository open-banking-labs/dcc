package cn.org.openbanking.dcc.application.reference;

import java.util.List;

import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.reference.repository.StandardReferenceRepository;
import cn.org.openbanking.dcc.core.standard.reference.ReferenceChecker;

import org.springframework.stereotype.Component;

/** Blocks deleting a data standard that is still referenced by an interface template. */
@Component
public class TemplateReferenceChecker implements ReferenceChecker {

    private final StandardReferenceRepository references;

    public TemplateReferenceChecker(StandardReferenceRepository references) {
        this.references = references;
    }

    @Override
    public List<String> findReferences(String tenantId, Long standardId) {
        return references.findByTenantIdAndStandardId(tenantId, standardId).stream()
                .filter(reference -> reference.getRefType() == ReferenceType.INTERFACE_TEMPLATE)
                .map(reference -> "interface template " + reference.getRefObjectCode())
                .distinct()
                .toList();
    }
}
