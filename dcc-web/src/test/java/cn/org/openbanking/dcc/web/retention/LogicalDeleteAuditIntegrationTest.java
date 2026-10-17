package cn.org.openbanking.dcc.web.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cn.org.openbanking.dcc.AbstractIntegrationTest;
import cn.org.openbanking.dcc.application.audit.AuditService;
import cn.org.openbanking.dcc.application.audit.AuditService.AuditView;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.DataStandardService.CreateCommand;
import cn.org.openbanking.dcc.application.standard.DataStandardService.UpdateCommand;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.application.tenant.TenantService;
import cn.org.openbanking.dcc.core.common.audit.AuditAction;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.TenantInactiveException;
import cn.org.openbanking.dcc.core.standard.StandardContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for retention (logical deletion) and the append-only audit trail
 * against a real PostgreSQL container.
 */
class LogicalDeleteAuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;
    @Autowired
    private TenantEnvironmentService environmentService;
    @Autowired
    private TenantApplicationService applicationService;
    @Autowired
    private DataStandardService standardService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private JdbcTemplate jdbc;

    private record Scope(String tenant, Long environmentId, Long applicationId) {
    }

    private Scope newScope() {
        String tenant = "t-" + UUID.randomUUID();
        tenantService.create(new TenantService.CreateCommand(tenant, "Tenant " + tenant, null));
        var environment = environmentService.create(tenant,
                new TenantEnvironmentService.CreateCommand("DEV", "Development", null, 1));
        var application = applicationService.create(tenant,
                new TenantApplicationService.CreateCommand("core", "Core", null));
        return new Scope(tenant, environment.id(), application.id());
    }

    private static StandardContent content(String name) {
        return new StandardContent(name, null, "VARCHAR", 16, null, true, null, null, null, "123456");
    }

    @Test
    void inactiveOrDeletedTenantIsRejectedAtEveryUseCaseBoundary() {
        Scope scope = newScope();

        // disabled via the platform path (not tenant-scoped, or re-enabling would deadlock)...
        tenantService.update(scope.tenant(), new TenantService.UpdateCommand(null, null, false));
        assertThatThrownBy(() -> standardService.search(scope.tenant(), null, null, null, null))
                .isInstanceOf(TenantInactiveException.class);

        // ...and the same claims work again once the status flips back
        tenantService.update(scope.tenant(), new TenantService.UpdateCommand(null, null, true));
        assertThat(standardService.search(scope.tenant(), null, null, null, null)).isEmpty();

        // logically-deleted tenants are hidden by the entity restriction -> inactive
        tenantService.delete(scope.tenant());
        assertThatThrownBy(() -> environmentService.list(scope.tenant()))
                .isInstanceOf(TenantInactiveException.class);
    }

    @Test
    void logicalDeleteKeepsTheRowButHidesItFromQueries() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("acctno", "account", content("账号")));

        standardService.delete(scope.tenant(), created.id());

        // gone from the default view...
        assertThatThrownBy(() -> standardService.get(scope.tenant(), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(standardService.search(scope.tenant(), null, null, null, null)).isEmpty();
        // ...but still physically present, carrying the retention marker
        Integer retained = jdbc.queryForObject(
                "select count(*) from data_standard where id = ? and deleted_at is not null", Integer.class,
                created.id());
        assertThat(retained).isEqualTo(1);
    }

    @Test
    void auditTrailRecordsCreateUpdateAndDeleteWithAChainedHash() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("acctno", "account", content("账号")));
        standardService.update(scope.tenant(), created.id(),
                new UpdateCommand("classification", content("账号 (updated)")));
        standardService.delete(scope.tenant(), created.id());

        List<AuditView> history = auditService.history(scope.tenant(), "DataStandard", created.id());

        assertThat(history).extracting(AuditView::action)
                .startsWith(AuditAction.CREATE)
                .endsWith(AuditAction.LOGICAL_DELETE)
                .contains(AuditAction.UPDATE);
        assertThat(history).allSatisfy(entry -> assertThat(entry.actor()).isEqualTo("system"));

        // the hash chain links each entry to the previous one for the same row
        assertThat(history.get(0).previousHash()).isNull();
        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i).previousHash()).isEqualTo(history.get(i - 1).hash());
        }

        AuditView delete = history.get(history.size() - 1);
        assertThat(delete.beforeState()).contains("\"deletedAt\":null");
        assertThat(delete.afterState()).contains("\"deletedAt\":\"");
        assertThat(delete.changedFields()).contains("deletedAt", "deletedBy");
    }

    @Test
    void restoreBringsARecordBackAndIsAudited() {        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("acctno", "account", content("账号")));
        standardService.delete(scope.tenant(), created.id());

        var restored = standardService.restore(scope.tenant(), created.id());

        assertThat(restored.id()).isEqualTo(created.id());
        assertThat(standardService.get(scope.tenant(), created.id()).code()).isEqualTo("acctno");
        assertThat(standardService.search(scope.tenant(), null, null, null, null))
                .extracting(v -> v.id()).contains(created.id());
        Integer live = jdbc.queryForObject(
                "select count(*) from data_standard where id = ? and deleted_at is null", Integer.class,
                created.id());
        assertThat(live).isEqualTo(1);

        List<AuditView> history = auditService.history(scope.tenant(), "DataStandard", created.id());
        assertThat(history).extracting(AuditView::action).endsWith(AuditAction.RESTORE);
    }

    @Test
    void restoreConflictsWhenTheCodeWasReusedByALiveRecord() {
        Scope scope = newScope();
        var first = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("clash", null, content("clash")));
        standardService.delete(scope.tenant(), first.id());
        standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("clash", null, content("clash")));

        assertThatThrownBy(() -> standardService.restore(scope.tenant(), first.id()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void codeCanBeReusedAfterALogicalDelete() {
        Scope scope = newScope();
        var first = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("reuse", null, content("reuse")));
        standardService.delete(scope.tenant(), first.id());

        var second = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("reuse", null, content("reuse")));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(standardService.get(scope.tenant(), second.id()).code()).isEqualTo("reuse");
        // still exactly one live row, plus the retained deleted one
        Integer live = jdbc.queryForObject(
                "select count(*) from data_standard where code = ? and deleted_at is null", Integer.class, "reuse");
        assertThat(live).isEqualTo(1);
    }

    @Test
    void auditSearchSupportsActionTimeFiltersAndPaging() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("paged", null, content("paged")));
        standardService.update(scope.tenant(), created.id(), new UpdateCommand("account", content("paged")));
        standardService.delete(scope.tenant(), created.id());

        // action filter selects exactly the matching entries
        var updates = auditService.search(scope.tenant(), "DataStandard", created.id(), null, AuditAction.UPDATE,
                null, null, PageRequest.of(0, 50));
        assertThat(updates.getTotalElements()).isEqualTo(1);
        assertThat(updates.getContent().get(0).action()).isEqualTo(AuditAction.UPDATE);

        // time window filter: the window starts in the future, so it excludes everything
        var empty = auditService.search(scope.tenant(), "DataStandard", created.id(), null, null,
                Instant.now().plusSeconds(60), null, PageRequest.of(0, 50));
        assertThat(empty.getTotalElements()).isZero();

        // paging works: one entry per page across the three recorded actions of the row
        for (int page = 0; page < 3; page++) {
            var slice = auditService.search(scope.tenant(), "DataStandard", created.id(), null, null,
                    null, null, PageRequest.of(page, 1));
            assertThat(slice.getContent()).hasSize(1);
        }
        var last = auditService.search(scope.tenant(), "DataStandard", created.id(), null, null,
                null, null, PageRequest.of(3, 1));
        assertThat(last.getContent()).isEmpty();
    }

    @Test
    void verifyReportsTheChainAsIntactOrBroken() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("tamper", null, content("tamper")));
        standardService.update(scope.tenant(), created.id(), new UpdateCommand("account", content("tamper")));

        // every entry carries its frozen compliance cutoff
        Boolean cutoffSet = jdbc.queryForObject(
                "select retention_until > occurred_at from audit_log where entity_id = ? limit 1",
                Boolean.class, created.id());
        assertThat(cutoffSet).isTrue();

        assertThat(auditService.verify(scope.tenant(), "DataStandard", created.id()).intact()).isTrue();

        // Tampering with a stored payload breaks the chain. The original bytes are put back
        // afterwards so the shared container keeps an intact, honest trail.
        Long tamperedId = jdbc.queryForObject(
                "select id from audit_log where entity_id = ? and action = 'UPDATE' limit 1", Long.class,
                created.id());
        String originalState = jdbc.queryForObject(
                "select after_state from audit_log where id = ?", String.class, tamperedId);
        jdbc.update("update audit_log set after_state = ? where id = ?", "{\"name\":\"forged\"}", tamperedId);

        var broken = auditService.verify(scope.tenant(), "DataStandard", created.id());
        assertThat(broken.intact()).isFalse();
        assertThat(broken.problems()).singleElement().asString()
                .contains("hash mismatch", "entry " + tamperedId);

        jdbc.update("update audit_log set after_state = ? where id = ?", originalState, tamperedId);
        assertThat(auditService.verify(scope.tenant(), "DataStandard", created.id()).intact()).isTrue();
    }

    @Test
    void verifyDetectsAMissingEntry() {
        Scope scope = newScope();
        var created = standardService.create(scope.tenant(), scope.environmentId(), scope.applicationId(),
                new CreateCommand("gap", null, content("gap")));
        standardService.update(scope.tenant(), created.id(), new UpdateCommand("account", content("gap")));
        standardService.delete(scope.tenant(), created.id());

        // Removing a middle entry is detected through the previous_hash linkage. (Removing
        // the tail alone is undetectable within an entity — that is inherent to hash chains;
        // cross-entity checks would need a global anchor.)
        jdbc.update("delete from audit_log where entity_id = ? and action = 'UPDATE'", created.id());

        var broken = auditService.verify(scope.tenant(), "DataStandard", created.id());
        assertThat(broken.intact()).isFalse();
        // the surviving successor (LOGICAL_DELETE) is the entry that no longer links
        Long successorId = jdbc.queryForObject(
                "select id from audit_log where entity_id = ? and action = 'LOGICAL_DELETE'", Long.class,
                created.id());
        assertThat(broken.problems()).singleElement().asString()
                .contains("previous_hash", "removed", "entry " + successorId);
        // the surviving entries' own hashes still verify, so the mismatch is about linkage only
    }
}
