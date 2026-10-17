package cn.org.openbanking.dcc.core.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import cn.org.openbanking.dcc.core.tenant.Tenant;

import org.junit.jupiter.api.Test;

/** The audit snapshots must be deterministic so the hash chain is stable. */
class AuditSnapshotTest {

    @Test
    void capturesBaseAndDeclaredFieldsInDeterministicOrder() {
        Tenant tenant = new Tenant("acme", "Acme", "description");

        Map<String, Object> snapshot = AuditSnapshot.capture(tenant);

        assertThat(snapshot).containsKeys("id", "code", "name", "description", "enabled",
                "createdAt", "updatedAt", "deletedAt", "deletedBy", "deletionReason");
        assertThat(new ArrayList<>(snapshot.keySet())).isSorted();
        // the transient audit baseline is never captured
        assertThat(snapshot).doesNotContainKey("auditBaseline");
    }

    @Test
    void changesReportsOnlyDifferingFields() {
        Tenant before = new Tenant("acme", "Acme", null);
        Map<String, Object> beforeImage = AuditSnapshot.capture(before);

        Tenant after = new Tenant("acme", "Acme Renamed", null);
        after.markDeleted("alice", "retention");
        Map<String, Object> afterImage = AuditSnapshot.capture(after);

        Map<String, Object> changes = AuditSnapshot.changes(beforeImage, afterImage);

        assertThat(changes).containsKeys("name", "deletedAt", "deletedBy", "deletionReason");
        assertThat(changes).doesNotContainKeys("code", "enabled");
        assertThat(changes.get("name")).isEqualTo("Acme Renamed");
    }

    @Test
    void jsonIsDeterministicAndEscapesStrings() {
        Map<String, Object> image = AuditSnapshot.capture(new Tenant("a\"b", "line\nname", null));

        String first = AuditJson.write(image);
        String second = AuditJson.write(image);

        assertThat(first).isEqualTo(second).contains("\"code\":\"a\\\"b\"").contains("\"name\":\"line\\nname\"");
        assertThat(first).contains("\"createdAt\":null");
        assertThat(first).doesNotContain("\"tenantId\"");
    }

    @Test
    void markDeletedThenRestoreClearsTheMarker() {
        Tenant tenant = new Tenant("acme", "Acme", null);

        tenant.markDeleted("alice", "gdpr");

        assertThat(tenant.isDeleted()).isTrue();
        assertThat(tenant.getDeletedBy()).isEqualTo("alice");
        assertThat(tenant.getDeletionReason()).isEqualTo("gdpr");
        assertThat(tenant.getDeletedAt()).isBeforeOrEqualTo(Instant.now());

        tenant.restore();

        assertThat(tenant.isDeleted()).isFalse();
        assertThat(tenant.getDeletedAt()).isNull();
        assertThat(tenant.getDeletedBy()).isNull();
        assertThat(tenant.getDeletionReason()).isNull();
    }
}
