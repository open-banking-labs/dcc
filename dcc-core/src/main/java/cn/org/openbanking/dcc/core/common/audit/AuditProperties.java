package cn.org.openbanking.dcc.core.common.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the audit trail, bound from {@code dcc.audit.*}.
 *
 * <pre>
 * dcc:
 *   audit:
 *     retention-years: 7     # compliance cutoff recorded on every entry (audit_log.retention_until)
 *     sweep-enabled: false   # scheduled report of entries past their cutoff (no purge)
 *     sweep-cron: "0 0 3 * * *"
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.audit")
public class AuditProperties {

    /**
     * Compliance retention period for audit entries. The cutoff ({@code retention_until}) is
     * computed per entry when it is written, so changing this later never retro-extends or
     * shortens entries already on the trail.
     */
    private int retentionYears = 7;

    /**
     * Whether a scheduled sweep reports entries whose retention cutoff has passed. The sweep
     * is report-only: the trail is append-only and nothing is ever removed by it.
     */
    private boolean sweepEnabled = false;

    /** Cron expression for the retention sweep ({@link AuditProperties#isSweepEnabled()}). */
    private String sweepCron = "0 0 3 * * *";

    public int getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(int retentionYears) {
        this.retentionYears = retentionYears;
    }

    public boolean isSweepEnabled() {
        return sweepEnabled;
    }

    public void setSweepEnabled(boolean sweepEnabled) {
        this.sweepEnabled = sweepEnabled;
    }

    public String getSweepCron() {
        return sweepCron;
    }

    public void setSweepCron(String sweepCron) {
        this.sweepCron = sweepCron;
    }
}
