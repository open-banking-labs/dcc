package cn.org.openbanking.dcc.core.announcement;

import java.time.Instant;

import cn.org.openbanking.dcc.core.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A system announcement (系统公告): the banner shown to every signed-in console user.
 *
 * <p>Extends {@link BaseEntity} rather than the tenant-scoped base <em>on purpose</em>:
 * {@code tenantId} is nullable and a {@code null} value marks a platform-wide announcement. A
 * platform row must be readable by every tenant, so the Hibernate {@code tenantFilter} is
 * deliberately not applied to this entity — it would hide exactly the rows the banner exists to
 * show. Isolation therefore rests on the explicit
 * {@code tenant_id is null or tenant_id = :tenantId} predicate in the repository, the same shape
 * {@code audit_log} and {@code role_assignment} use. Changing this class to
 * {@code TenantScopedEntity} would silently make platform announcements invisible.
 *
 * <p>Retention and the audit trail come from {@link BaseEntity}: every create, update and logical
 * delete is recorded by the audit listener, and nothing is ever physically removed.
 */
@Entity
@Table(name = "announcement",
        indexes = @Index(name = "ix_announcement_visible", columnList = "tenant_id,enabled"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

    /** Owning tenant, or {@code null} for a platform announcement visible to every tenant. */
    @Column(name = "tenant_id", length = 64, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 16)
    private AnnouncementLevel level;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Plain text only — the console renders it as text, never as markup. */
    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** Switching this off hides the announcement without deleting it (or its audit history). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Optional start of the visibility window; {@code null} means "visible immediately". */
    @Column(name = "starts_at")
    private Instant startsAt;

    /** Optional end of the visibility window; {@code null} means "until disabled". */
    @Column(name = "ends_at")
    private Instant endsAt;

    /**
     * @param tenantId the owning tenant, or {@code null} for a platform announcement
     */
    public Announcement(String tenantId, AnnouncementLevel level, String title, String body,
            Instant startsAt, Instant endsAt) {
        this.tenantId = tenantId;
        this.level = level;
        this.title = title;
        this.body = body;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
