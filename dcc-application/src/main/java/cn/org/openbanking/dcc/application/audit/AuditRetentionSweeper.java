package cn.org.openbanking.dcc.application.audit;

import java.time.Instant;

import cn.org.openbanking.dcc.core.common.audit.AuditLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled compliance sweep over the audit trail. Report-only: it flags entries whose
 * {@code retention_until} cutoff has passed so the position can be reported to compliance;
 * it never removes anything — the trail stays append-only until an explicitly approved
 * (and separately implemented) purge process exists.
 */
@Component
public class AuditRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionSweeper.class);

    private final AuditLogRepository auditLog;

    public AuditRetentionSweeper(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    /** @return how many entries in the whole trail are past their compliance cutoff. */
    public long pastRetention() {
        return auditLog.countByRetentionUntilBefore(Instant.now());
    }

    @Scheduled(cron = "${dcc.audit.sweep-cron:0 0 3 * * *}")
    public void sweep() {
        long expired = pastRetention();
        if (expired > 0) {
            log.warn("audit trail: {} entr{} past their retention_until cutoff — report to compliance; "
                    + "rows are retained until an approved purge runs", expired, expired == 1 ? "y" : "ies");
        }
    }
}
