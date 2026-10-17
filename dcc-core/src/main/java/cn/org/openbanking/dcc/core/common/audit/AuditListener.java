package cn.org.openbanking.dcc.core.common.audit;

import java.util.Map;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import org.springframework.stereotype.Component;

/**
 * JPA entity listener attached to every {@link BaseEntity} that feeds the append-only
 * audit trail.
 *
 * <ul>
 *   <li>{@link #onLoad} caches the pre-mutation image so an update can be told apart from
 *       a logical delete and its before-image recorded.</li>
 *   <li>{@link #onCreate} records inserts (after the identity id is assigned).</li>
 *   <li>{@link #onUpdate} records content updates and logical deletions.</li>
 *   <li>{@link #onRemove} records a physical delete, which under retention should not
 *       happen — its presence is a signal worth alerting on.</li>
 * </ul>
 *
 * <p>It is a Spring bean so {@link AuditRecorder} (and through it the identity provider) can
 * be injected; Hibernate instantiates entity listeners through Spring's bean container.
 */
@Component
public class AuditListener {

    private final AuditRecorder recorder;

    public AuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @PostLoad
    public void onLoad(Object entity) {
        if (entity instanceof BaseEntity base) {
            base.setAuditBaseline(AuditSnapshot.capture(entity));
            // Register early: a transaction that only loads then modifies this row flushes
            // at commit, so without this the before-image would never be written.
            recorder.ensureRegistered();
        }
    }

    @PostPersist
    public void onCreate(Object entity) {
        if (entity instanceof BaseEntity) {
            recorder.record((BaseEntity) entity, AuditAction.CREATE, null, AuditSnapshot.capture(entity));
        }
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        if (!(entity instanceof BaseEntity base)) {
            return;
        }
        Map<String, Object> before = base.getAuditBaseline();
        boolean wasDeleted = before != null && before.get("deletedAt") != null;
        AuditAction action;
        if (base.isDeleted() && !wasDeleted) {
            action = AuditAction.LOGICAL_DELETE;
        } else if (!base.isDeleted() && wasDeleted) {
            action = AuditAction.RESTORE;
        } else {
            action = AuditAction.UPDATE;
        }
        recorder.record(base, action, before, AuditSnapshot.capture(entity));
    }

    @PreRemove
    public void onRemove(Object entity) {
        if (entity instanceof BaseEntity base) {
            recorder.record(base, AuditAction.PHYSICAL_DELETE, base.getAuditBaseline(), null);
        }
    }
}
