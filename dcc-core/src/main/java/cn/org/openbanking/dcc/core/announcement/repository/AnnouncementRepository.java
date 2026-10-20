package cn.org.openbanking.dcc.core.announcement.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.announcement.Announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Announcement}.
 *
 * <p>Unlike the artifact repositories, these queries are <em>not</em> all tenant-scoped: a platform
 * announcement has {@code tenant_id is null} and must reach every tenant. Every read therefore
 * states its scope explicitly instead of relying on the Hibernate tenant filter (which this entity
 * does not declare, see {@link Announcement}).
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // ------------------------------------------------------------------ tenant scope

    /** One tenant-owned announcement; a platform announcement is not reachable through this. */
    Optional<Announcement> findByTenantIdAndId(String tenantId, Long id);

    /** Every announcement owned by the tenant, newest first, for the management screen. */
    List<Announcement> findByTenantIdOrderByIdDesc(String tenantId);

    // ------------------------------------------------------------------ platform scope

    /**
     * One platform announcement. Deliberately cannot load a tenant announcement: the platform
     * endpoints must never be able to edit another tenant's notice.
     */
    Optional<Announcement> findByIdAndTenantIdIsNull(Long id);

    /** Every platform announcement, newest first, for the platform management screen. */
    List<Announcement> findByTenantIdIsNullOrderByIdDesc();

    // ------------------------------------------------------------------ banner

    /**
     * The banner payload: platform announcements plus the caller's own, enabled, inside their
     * visibility window.
     *
     * <p>The {@code tenant_id is null or tenant_id = :tenantId} predicate is the isolation
     * guarantee — a tenant must never see another tenant's announcement. Ordering is newest-first
     * only; the urgency ordering is applied by the use case so it stays visible in one place.
     */
    @Query("select a from Announcement a "
            + "where (a.tenantId is null or a.tenantId = :tenantId) "
            + "and a.enabled = true "
            + "and (a.startsAt is null or a.startsAt <= :now) "
            + "and (a.endsAt is null or a.endsAt >= :now) "
            + "order by a.id desc")
    List<Announcement> findVisible(@Param("tenantId") String tenantId, @Param("now") Instant now);
}
