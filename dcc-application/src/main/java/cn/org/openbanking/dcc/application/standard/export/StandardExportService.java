package cn.org.openbanking.dcc.application.standard.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.ScopeValidation;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.standard.excel.StandardExcelService;
import cn.org.openbanking.dcc.application.standard.excel.StandardExcelService.StandardExportRow;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.DataStandardVersion;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.export.ExportJob;
import cn.org.openbanking.dcc.core.standard.export.ExportJobStatus;
import cn.org.openbanking.dcc.core.standard.export.repository.ExportJobRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardVersionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous standards export.
 *
 * <p>A request only enqueues a job; a worker running in dcc-web turns it into an
 * .xlsx file and uploads it to S3-compatible storage. The caller receives a
 * short-lived pre-signed link, so the application never streams the file. All
 * database work runs inside {@link TenantScope} so both the explicit
 * {@code tenant_id} predicates and the Hibernate tenant filter apply to background
 * work exactly as they do to a request.
 */
@Service
public class StandardExportService {

    private static final Logger log = LoggerFactory.getLogger(StandardExportService.class);

    private final ExportJobRepository jobs;
    private final ExportJobQueue queue;
    private final DataStandardRepository standards;
    private final DataStandardVersionRepository versions;
    private final StandardExcelService excel;
    private final ExportStorage storage;
    private final ExportProperties properties;
    private final TenantScope tenantScope;
    private final ScopeValidation scopeValidation;
    private final ChangeContext changeContext;

    public StandardExportService(ExportJobRepository jobs,
            ExportJobQueue queue,
            DataStandardRepository standards,
            DataStandardVersionRepository versions,
            StandardExcelService excel,
            ExportStorage storage,
            ExportProperties properties,
            TenantScope tenantScope,
            ScopeValidation scopeValidation,
            ChangeContext changeContext) {
        this.jobs = jobs;
        this.queue = queue;
        this.standards = standards;
        this.versions = versions;
        this.excel = excel;
        this.storage = storage;
        this.properties = properties;
        this.tenantScope = tenantScope;
        this.scopeValidation = scopeValidation;
        this.changeContext = changeContext;
    }

    // ------------------------------------------------------------------ commands

    public record CreateExportCommand(
            @NotNull Long environmentId,
            @NotNull Long applicationId,
            @Size(max = 128) String keyword,
            @Size(max = 64) String category,
            StandardStatus status,
            List<Long> ids) {
    }

    public record ExportJobView(Long id, String status, Integer rowCount, String fileName,
            String downloadUrl, Instant expiresAt, String errorMessage, Instant createdAt) {
    }

    // ------------------------------------------------------------------ use cases

    /** Enqueues one export. At most one PENDING/RUNNING job exists per tenant. */
    @Transactional
    public ExportJobView create(String tenantId, CreateExportCommand command) {
        scopeValidation.requireSurface(tenantId, command.environmentId(), command.applicationId());
        List<Long> ids = command.ids() == null ? List.of()
                : command.ids().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > properties.getMaxSelected()) {
            throw new ValidationException("at most " + properties.getMaxSelected() + " standards can be exported at once");
        }
        String keyword = trimToNull(command.keyword());
        String category = trimToNull(command.category());
        if (jobs.existsByTenantIdAndStatusIn(tenantId,
                List.of(ExportJobStatus.PENDING, ExportJobStatus.RUNNING))) {
            throw new ConflictException("该租户已有导出任务在进行中，请稍后重试");
        }
        String selectedIds = ids.isEmpty() ? null
                : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (selectedIds != null && selectedIds.length() > 2048) {
            throw new ValidationException("too many selected standards");
        }
        ExportJob job = new ExportJob(tenantId, changeContext.author(), command.environmentId(),
                command.applicationId(), keyword, category, command.status(), selectedIds);
        try {
            job = jobs.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index is the cross-instance guard for the same rule.
            throw new ConflictException("该租户已有导出任务在进行中，请稍后重试");
        }
        return toView(job);
    }

    @Transactional(readOnly = true)
    public ExportJobView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> jobs.findByTenantIdAndId(tenantId, id)
                .map(this::toView)
                .orElseThrow(() -> ResourceNotFoundException.of("ExportJob", id)));
    }

    /** Generates and uploads one job, if any is pending. Never throws to the scheduler. */
    public void processNextJob() {
        Optional<Long> claimed = queue.claimNext();
        if (claimed.isEmpty()) {
            return;
        }
        ExportJob job = jobs.findById(claimed.get()).orElse(null);
        if (job == null) {
            return;
        }
        Path file = null;
        try {
            file = Files.createTempFile("dcc-standards-export-", ".xlsx");
            Path target = file;
            int rows = tenantScope.call(job.getTenantId(), () -> {
                try {
                    return excel.write(target, rowIterator(job));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
            String key = objectKey(job);
            storage.put(key, file);
            job.markReady(key, fileName(job), rows, Instant.now().plus(properties.getRetention()));
        } catch (IOException | RuntimeException ex) {
            log.error("data standard export job {} failed", job.getId(), ex);
            job.markFailed(truncate(failureMessage(ex), 1024));
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // A stale temp file is harmless; the OS cleans it up.
                }
            }
            try {
                jobs.save(job);
            } catch (RuntimeException ex) {
                log.error("cannot persist export job {} result", job.getId(), ex);
            }
        }
    }

    /** Marks RUNNING jobs that outlived the worker as failed (crash/restart recovery). */
    @Transactional
    public int failStaleJobs() {
        Instant now = Instant.now();
        return jobs.failStaleRunning(ExportJobStatus.RUNNING, ExportJobStatus.FAILED,
                "导出任务中断，请重新发起", now,
                now.minus(Duration.ofMinutes(properties.getStaleAfterMinutes())));
    }

    /** Deletes objects past their retention window and marks their jobs EXPIRED. */
    public void cleanupExpired() {
        Instant now = Instant.now();
        for (ExportJob job : jobs.findTop200ByStatusAndExpiresAtBeforeOrderByIdAsc(
                ExportJobStatus.READY, now)) {
            try {
                if (job.getObjectKey() != null) {
                    storage.delete(job.getObjectKey());
                }
                job.markExpired();
                jobs.save(job);
            } catch (RuntimeException ex) {
                log.warn("cannot clean up export job {}: {}", job.getId(), ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ rows

    /**
     * Lazily yields export rows. The filter path pages through the same query the
     * console uses; the selected-ids path performs two batch lookups. Both stay
     * inside the caller's tenant scope.
     */
    private Iterator<StandardExportRow> rowIterator(ExportJob job) {
        String tenantId = job.getTenantId();
        List<Long> ids = job.parsedIds();
        if (!ids.isEmpty()) {
            List<DataStandard> found = standards.findByTenantIdAndIdIn(tenantId, ids).stream()
                    .filter(standard -> Objects.equals(standard.getEnvironmentId(), job.getEnvironmentId()))
                    .filter(standard -> Objects.equals(standard.getApplicationId(), job.getApplicationId()))
                    .sorted(Comparator.comparing(DataStandard::getCode))
                    .toList();
            return toRows(tenantId, found).iterator();
        }

        String keyword = DataStandardService.keywordPattern(job.getKeyword());
        return new Iterator<>() {
            private int page;
            private boolean exhausted;
            private Iterator<StandardExportRow> current = List.<StandardExportRow>of().iterator();
            private int emitted;

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && !exhausted) {
                    Page<DataStandard> found = standards.searchPage(tenantId, job.getEnvironmentId(),
                            job.getApplicationId(), job.getCategory(), job.getStatusFilter(), keyword,
                            PageRequest.of(page++, properties.getPageChunkSize()));
                    if (!found.hasNext()) {
                        exhausted = true;
                    }
                    current = toRows(tenantId, found.getContent()).iterator();
                }
                return current.hasNext();
            }

            @Override
            public StandardExportRow next() {
                StandardExportRow row = current.next();
                if (++emitted > properties.getMaxRows()) {
                    throw new ValidationException("导出结果超过 " + properties.getMaxRows()
                            + " 行，请缩小筛选范围");
                }
                return row;
            }
        };
    }

    private List<StandardExportRow> toRows(String tenantId, List<DataStandard> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> versionIds = rows.stream()
                .map(DataStandard::getCurrentVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, DataStandardVersion> byId = versionIds.isEmpty() ? Map.of()
                : versions.findByTenantIdAndIdIn(tenantId, versionIds).stream()
                        .collect(Collectors.toMap(DataStandardVersion::getId, Function.identity()));
        List<StandardExportRow> result = new ArrayList<>(rows.size());
        for (DataStandard standard : rows) {
            DataStandardVersion version = byId.get(standard.getCurrentVersionId());
            if (version == null) {
                continue;
            }
            StandardContent content = version.content();
            result.add(new StandardExportRow(standard.getCode(), standard.getCategory(), content.name(),
                    content.dataType(), content.length(), content.scale(), content.required(),
                    content.defaultValue(), content.enumValues(), content.regex(), content.exampleValue(),
                    content.description(), standard.getStatus(), standard.getCurrentVersion(),
                    standard.getUpdatedAt()));
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private ExportJobView toView(ExportJob job) {
        String status = job.getStatus().name();
        String downloadUrl = null;
        if (job.getStatus() == ExportJobStatus.READY) {
            if (job.getExpiresAt() != null && job.getExpiresAt().isAfter(Instant.now())
                    && job.getObjectKey() != null) {
                try {
                    downloadUrl = storage.presign(job.getObjectKey(), job.getFileName());
                } catch (RuntimeException ex) {
                    log.warn("cannot sign export link for job {}: {}", job.getId(), ex.getMessage());
                }
            } else {
                status = ExportJobStatus.EXPIRED.name();
            }
        }
        return new ExportJobView(job.getId(), status, job.getRowCount(), job.getFileName(), downloadUrl,
                job.getExpiresAt(), job.getErrorMessage(), job.getCreatedAt());
    }

    private static String objectKey(ExportJob job) {
        String tenant = job.getTenantId().replaceAll("[^A-Za-z0-9._-]", "_");
        return "standards-export/" + tenant + "/" + job.getId() + ".xlsx";
    }

    private static String fileName(ExportJob job) {
        return "standards-export-" + job.getId() + ".xlsx";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String failureMessage(Exception ex) {
        Throwable cause = ex instanceof UncheckedIOException && ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
