package cn.org.openbanking.dcc.core.standard.export.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.export.ExportJob;
import cn.org.openbanking.dcc.core.standard.export.ExportJobStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link ExportJob}.
 *
 * <p>The entity has no tenant filter (it is operational bookkeeping, not a business
 * record), so every query states its tenant predicate explicitly. The claim query
 * uses {@code for update skip locked}: several application instances can poll the
 * same queue without processing a job twice.
 */
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {

    Optional<ExportJob> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndStatusIn(String tenantId, Collection<ExportJobStatus> statuses);

    @Query(value = "select * from export_job where status = 'PENDING' "
            + "order by id limit 1 for update skip locked", nativeQuery = true)
    Optional<ExportJob> lockNextPending();

    List<ExportJob> findTop200ByStatusAndExpiresAtBeforeOrderByIdAsc(ExportJobStatus status, Instant cutoff);

    @Modifying
    @Query("update ExportJob j set j.status = :failed, j.errorMessage = :message, "
            + "j.finishedAt = :now where j.status = :running and j.startedAt < :cutoff")
    int failStaleRunning(@Param("running") ExportJobStatus running,
            @Param("failed") ExportJobStatus failed,
            @Param("message") String message,
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff);
}
