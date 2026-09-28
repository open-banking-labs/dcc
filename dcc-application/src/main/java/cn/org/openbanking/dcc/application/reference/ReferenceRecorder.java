package cn.org.openbanking.dcc.application.reference;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.reference.StandardReference;
import cn.org.openbanking.dcc.core.reference.repository.StandardReferenceRepository;

import org.springframework.stereotype.Component;

/**
 * Maintains the recorded standard references for an artifact. Called by the
 * table/interface/template services whenever they save a version, so the
 * "where used" lookup and the delete guard stay correct.
 */
@Component
public class ReferenceRecorder {

    private final StandardReferenceRepository references;

    public ReferenceRecorder(StandardReferenceRepository references) {
        this.references = references;
    }

    /** Replaces all references of one artifact with the given set of standard ids. */
    public void replaceAll(String tenantId, ReferenceType type, Long objectId, String objectCode,
            Collection<Long> standardIds) {
        references.deleteByScope(tenantId, type, objectId);
        for (Long standardId : new LinkedHashSet<>(standardIds)) {
            if (standardId != null) {
                StandardReference reference = new StandardReference(standardId, type, objectId, objectCode);
                reference.setTenantId(tenantId);
                references.save(reference);
            }
        }
    }

    /** Removes all references held by an artifact (used when the artifact is deleted). */
    public void removeAll(String tenantId, ReferenceType type, Long objectId) {
        references.deleteByScope(tenantId, type, objectId);
    }

    /** @return the distinct non-null standard ids. */
    public static Collection<Long> distinct(Collection<Long> standardIds) {
        return standardIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }
}
