package cn.org.openbanking.dcc.application.dashboard;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.core.common.audit.AuditLogRepository;
import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceDefinitionRepository;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;
import cn.org.openbanking.dcc.core.migration.repository.MigrationOrderRepository;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.table.repository.TableStructureRepository;
import cn.org.openbanking.dcc.core.template.repository.InterfaceTemplateRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workbench dashboard: one aggregated read of the counts a signed-in user acts on.
 *
 * <p>Why an aggregate endpoint instead of N list calls from the console: the artifact lists are
 * tenant-wide arrays, so a dashboard built on them would load every row of four tables on every
 * page load. The counts here run as SQL {@code count} in the database.
 *
 * <p>Two rules shape the response:
 * <ul>
 *   <li><b>Sections are permission-shaped.</b> The caller decides (in the exposure layer, from the
 *       authenticated authorities) which sections to include; a section the caller may not read is
 *       {@code null}, never an empty object — "no rows" and "not allowed" must stay distinguishable.
 *       This layer stays free of Spring Security types so it remains protocol-agnostic and
 *       unit-testable without a request context.</li>
 *   <li><b>No tenant means no tenant data.</b> A platform administrator has no tenant in
 *       {@code TenantContext}; every tenant-scoped section is then {@code null}. Falling through to
 *       a query with a {@code null} tenant id would silently return zero rows… which reads like
 *       "you have no artifacts" instead of "this does not apply to you".</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_LIMIT = 5;

    private static final int AUDIT_WINDOW_HOURS = 24;

    private final DataStandardRepository standards;
    private final TableStructureRepository tables;
    private final InterfaceDefinitionRepository interfaces;
    private final InterfaceTemplateRepository templates;
    private final MigrationOrderRepository migrations;
    private final AuditLogRepository auditLog;
    private final TenantRepository tenants;
    private final TenantScope tenantScope;

    public DashboardService(DataStandardRepository standards,
            TableStructureRepository tables,
            InterfaceDefinitionRepository interfaces,
            InterfaceTemplateRepository templates,
            MigrationOrderRepository migrations,
            AuditLogRepository auditLog,
            TenantRepository tenants,
            TenantScope tenantScope) {
        this.standards = standards;
        this.tables = tables;
        this.interfaces = interfaces;
        this.templates = templates;
        this.migrations = migrations;
        this.auditLog = auditLog;
        this.tenants = tenants;
        this.tenantScope = tenantScope;
    }

    /**
     * Which sections the caller may read. Resolved from the caller's authorities by the exposure
     * layer; the application layer only sees the outcome.
     *
     * @param environmentId optional scope filter, {@code null} means every environment
     * @param applicationId optional scope filter, {@code null} means every application
     */
    public record Query(Long environmentId, Long applicationId, boolean artifacts, boolean migrations,
            boolean businessAudit, boolean platformAudit, boolean tenants) {
    }

    /** One artifact kind: everything in scope, and how much of it is waiting for review. */
    public record ArtifactCounts(long total, long pendingReview) {
    }

    public record ArtifactSummary(ArtifactCounts dataStandard, ArtifactCounts tableStructure,
            ArtifactCounts interfaces, ArtifactCounts interfaceTemplates) {
    }

    /** Migration orders per lifecycle state, newest states first for the console's card. */
    public record MigrationSummary(long pendingSubmission, long pendingApproval, long approved, long migrated,
            long rejected, long rolledBack) {
    }

    /** A dashboard-shaped projection: enough to render a row and link to the order, nothing more. */
    public record RecentMigration(Long id, ArtifactType artifactType, String artifactCode, MigrationStatus status,
            Long targetEnvironmentId, Instant updatedAt) {
    }

    public record TenantSummary(long total, long enabled) {
    }

    public record AuditSummary(long last24h) {
    }

    public record SummaryView(String tenantId, ArtifactSummary artifacts, MigrationSummary migrations,
            List<RecentMigration> recentMigrations, AuditSummary audit, TenantSummary tenants) {
    }

    /**
     * @param tenantId the caller's tenant, or {@code null} for a platform-level caller
     * @param query    scope filters plus the sections the caller may read
     * @return a summary whose forbidden/unapplicable sections are {@code null}
     */
    public SummaryView summary(String tenantId, Query query) {
        if (tenantId == null) {
            // Platform-level caller: no tenant data plane to read. Only the tenant registry and the
            // platform audit trail are meaningful (and only if granted).
            return new SummaryView(null, null, null, null, auditSummary(null, query),
                    query.tenants() ? tenantSummary() : null);
        }
        return tenantScope.call(tenantId, () -> new SummaryView(
                tenantId,
                query.artifacts() ? artifactSummary(tenantId, query) : null,
                query.migrations() ? migrationSummary(tenantId, query) : null,
                query.migrations() ? recentMigrations(tenantId, query) : null,
                auditSummary(tenantId, query),
                query.tenants() ? tenantSummary() : null));
    }

    private ArtifactSummary artifactSummary(String tenantId, Query query) {
        Long environmentId = query.environmentId();
        Long applicationId = query.applicationId();
        return new ArtifactSummary(
                artifactCounts(standards.countInScope(tenantId, environmentId, applicationId, null),
                        standards.countInScope(tenantId, environmentId, applicationId,
                                StandardStatus.PENDING_REVIEW)),
                artifactCounts(tables.countInScope(tenantId, environmentId, applicationId, null),
                        tables.countInScope(tenantId, environmentId, applicationId,
                                StandardStatus.PENDING_REVIEW)),
                artifactCounts(interfaces.countInScope(tenantId, environmentId, applicationId, null),
                        interfaces.countInScope(tenantId, environmentId, applicationId,
                                StandardStatus.PENDING_REVIEW)),
                artifactCounts(templates.countInScope(tenantId, environmentId, applicationId, null),
                        templates.countInScope(tenantId, environmentId, applicationId,
                                StandardStatus.PENDING_REVIEW)));
    }

    private static ArtifactCounts artifactCounts(long total, long pendingReview) {
        return new ArtifactCounts(total, pendingReview);
    }

    private MigrationSummary migrationSummary(String tenantId, Query query) {
        Long targetEnvironmentId = query.environmentId();
        return new MigrationSummary(
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.PENDING_SUBMISSION),
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.PENDING_APPROVAL),
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.APPROVED),
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.MIGRATED),
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.REJECTED),
                migrations.countInScope(tenantId, targetEnvironmentId, MigrationStatus.ROLLED_BACK));
    }

    private List<RecentMigration> recentMigrations(String tenantId, Query query) {
        return migrations.recent(tenantId, query.environmentId(), PageRequest.of(0, RECENT_LIMIT)).stream()
                .map(order -> new RecentMigration(order.getId(), order.getArtifactType(), order.getArtifactCode(),
                        order.getStatus(), order.getTargetEnvironmentId(), order.getUpdatedAt()))
                .toList();
    }

    /**
     * @param tenantId the caller's tenant, or {@code null} at platform level
     * @return the last-24h entry count, or {@code null} when the caller may read neither trail
     */
    private AuditSummary auditSummary(String tenantId, Query query) {
        if (query.businessAudit() && tenantId != null) {
            return new AuditSummary(countAudit(tenantId));
        }
        if (query.platformAudit()) {
            // a null tenant selects the platform-level rows (tenant_id is null) in the audit trail
            return new AuditSummary(countAudit(null));
        }
        return null;
    }

    private long countAudit(String tenantIdOrNull) {
        Instant from = Instant.now().minus(AUDIT_WINDOW_HOURS, ChronoUnit.HOURS);
        return auditLog.search(tenantIdOrNull, null, null, null, null, from, null, PageRequest.of(0, 1))
                .getTotalElements();
    }

    private TenantSummary tenantSummary() {
        // the tenant registry is platform-level: these two counts are global by design
        return new TenantSummary(tenants.count(), tenants.countByEnabledTrue());
    }
}
