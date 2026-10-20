package cn.org.openbanking.dcc.application.standard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.ContentDigest;
import cn.org.openbanking.dcc.application.common.SeparationOfDuties;
import cn.org.openbanking.dcc.application.common.ScopeValidation;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.migration.MigrationPolicy;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.DataStandardVersion;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.diff.ContentDiffer;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.reference.ReferenceChecker;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardVersionRepository;
import cn.org.openbanking.dcc.core.standard.version.SemVer;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionBumpPolicy;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for data standards (数标): CRUD, versioning (history/diff/rollback),
 * lifecycle transitions, filtered search, import/export and delete-with-reference-check.
 *
 * <p>Versioning follows the master-record + version-record model: every content
 * change writes a new immutable {@link DataStandardVersion} snapshot and the
 * semantic-version bump is derived from the field-level diff
 * ({@link VersionBumpPolicy}). History is never rewritten; rollback is a forward
 * new version.
 */
@Service
@Transactional
public class DataStandardService {

    /** Upper bound for the console's batch summary/reference-analysis selections. */
    public static final int MAX_BATCH_IDS = 100;

    private final DataStandardRepository standards;
    private final DataStandardVersionRepository versions;
    private final TenantScope tenantScope;
    private final List<ReferenceChecker> referenceCheckers;
    private final MigrationPolicy migrationPolicy;
    private final ScopeValidation scopeValidation;
    private final ContentDigest digest;
    private final ChangeContext changeContext;
    private final SeparationOfDuties separationOfDuties;

    public DataStandardService(DataStandardRepository standards,
            DataStandardVersionRepository versions,
            TenantScope tenantScope,
            List<ReferenceChecker> referenceCheckers,
            MigrationPolicy migrationPolicy,
            ScopeValidation scopeValidation,
            ContentDigest digest,
            ChangeContext changeContext,
            SeparationOfDuties separationOfDuties) {
        this.standards = standards;
        this.versions = versions;
        this.tenantScope = tenantScope;
        this.referenceCheckers = referenceCheckers;
        this.migrationPolicy = migrationPolicy;
        this.scopeValidation = scopeValidation;
        this.digest = digest;
        this.changeContext = changeContext;
        this.separationOfDuties = separationOfDuties;
    }

    // ------------------------------------------------------------------ records

    public record CreateCommand(
            @NotBlank @Size(max = 128) String code,
            @Size(max = 64) String category,
            @NotNull StandardContent content) {
    }

    public record UpdateCommand(
            @Size(max = 64) String category,
            @NotNull StandardContent content) {
    }

    public record StandardView(Long id, String tenantId, Long environmentId, Long applicationId, String code,
            String category, StandardStatus status, String currentVersion, StandardContent content,
            Instant createdAt, Instant updatedAt) {
    }

    /**
     * Lightweight row for the query/maintenance tables and the reference-analysis
     * page. It deliberately omits the content snapshot so a page of results never
     * drags every standard's full detail across the wire.
     */
    public record StandardSummaryView(Long id, Long environmentId, Long applicationId, String code, String name,
            String category, String dataType, StandardStatus status, String currentVersion, Instant updatedAt) {
    }

    public record VersionView(Long id, String version, VersionChangeType changeType, String changeSummary,
            StandardContent content, Long sourceEnvironmentId, String sourceVersion, Instant createdAt,
            String contentHash, String parentHash, String author, String message) {
    }

    public record DiffView(String fromVersion, String toVersion, VersionChangeType changeType,
            List<FieldChange> changes) {
    }

    /**
     * Import outcome. When {@code errors} is non-empty nothing was written (the file
     * is validated in full before the first row is applied); the two-argument form
     * keeps the JSON import contract unchanged.
     */
    public record ImportResult(int created, int updated, List<RowError> errors) {
        public ImportResult(int created, int updated) {
            this(created, updated, List.of());
        }
    }

    /** One rejected spreadsheet row: 1-based Excel row number and a human-readable reason. */
    public record RowError(int row, String message) {
    }

    // ------------------------------------------------------------------ use cases

    public StandardView create(String tenantId, Long environmentId, Long applicationId, CreateCommand command) {
        migrationPolicy.requireDirectMaintenanceAllowed(tenantId, environmentId);
        scopeValidation.requireSurface(tenantId, environmentId, applicationId);
        return tenantScope.call(tenantId, () -> {
            validateContent(command.content());
            if (standards.existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                    tenantId, environmentId, applicationId, command.code())) {
                throw new ConflictException("data standard already exists: " + command.code());
            }
            DataStandard standard = new DataStandard(environmentId, applicationId, command.code(),
                    command.category(), StandardStatus.DRAFT, SemVer.INITIAL.value());
            standard.setTenantId(tenantId);
            standard = standards.save(standard);

            DataStandardVersion version = new DataStandardVersion(standard.getId(), SemVer.INITIAL.value(),
                    VersionChangeType.MAJOR, command.content(),
                    changeContext.messageOr(() -> "initial version"));
            version.setTenantId(tenantId);
            version.stamp(digest.digest(command.content()), null, changeContext.author(),
                    changeContext.messageOr(() -> "initial version"));
            version = versions.save(version);

            standard.pointToVersion(version.getId(), version.getVersion());
            return toView(standard);
        });
    }

    @Transactional(readOnly = true)
    public StandardView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> toView(requireStandard(tenantId, id)));
    }

    @Transactional(readOnly = true)
    public List<StandardView> search(String tenantId, Long environmentId, Long applicationId,
            String category, StandardStatus status) {
        return tenantScope.call(tenantId, () -> {
            List<DataStandard> rows = standards.search(tenantId, environmentId, applicationId, category, status);
            Map<Long, DataStandardVersion> current = currentVersions(tenantId, rows);
            return rows.stream()
                    .map(standard -> toView(standard, currentVersionOf(tenantId, standard, current)))
                    .toList();
        });
    }

    /**
     * Paged console search. A page costs two queries: the page itself and one batch
     * lookup of the current version snapshots (no per-row N+1).
     */
    @Transactional(readOnly = true)
    public Page<StandardSummaryView> query(String tenantId, Long environmentId, Long applicationId,
            String keyword, String category, StandardStatus status, int page, int size) {
        String pattern = keywordPattern(keyword);
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        return tenantScope.call(tenantId, () -> {
            Page<DataStandard> found = standards.searchPage(tenantId, environmentId, applicationId,
                    category, status, pattern, PageRequest.of(safePage, safeSize));
            Map<Long, DataStandardVersion> current = currentVersions(tenantId, found.getContent());
            return found.map(standard -> toSummary(standard, current.get(standard.getCurrentVersionId())));
        });
    }

    /**
     * Lightweight summaries for an explicit id selection (reference analysis). Keeps
     * the caller's order, drops ids the tenant cannot see, and caps the batch size.
     */
    @Transactional(readOnly = true)
    public List<StandardSummaryView> summaries(String tenantId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> requested = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (requested.size() > MAX_BATCH_IDS) {
            throw new ValidationException("at most " + MAX_BATCH_IDS + " standards can be selected");
        }
        return tenantScope.call(tenantId, () -> {
            List<DataStandard> found = standards.findByTenantIdAndIdIn(tenantId, new LinkedHashSet<>(requested));
            Map<Long, DataStandardVersion> current = currentVersions(tenantId, found);
            Map<Long, StandardSummaryView> byId = found.stream()
                    .collect(Collectors.toMap(DataStandard::getId,
                            standard -> toSummary(standard, current.get(standard.getCurrentVersionId()))));
            return requested.stream().map(byId::get).filter(Objects::nonNull).toList();
        });
    }

    /**
     * Escapes a user keyword into the lower-case {@code %pattern%} consumed by the
     * repository's {@code like ... escape '!'} predicate. Returns {@code null} for a
     * blank keyword (no constraint); {@code %} and {@code _} stay literal by design.
     */
    public static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > 128) {
            throw new ValidationException("keyword is too long");
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * Applies a content change by writing a new version. When the content is
     * unchanged only classification metadata (category) is updated and no version
     * is created.
     */
    public StandardView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            validateContent(command.content());
            DataStandard standard = requireStandard(tenantId, id);
            migrationPolicy.requireDirectMaintenanceAllowed(tenantId, standard.getEnvironmentId());
            DataStandardVersion current = requireVersion(tenantId, id, standard.getCurrentVersion());

            List<FieldChange> changes = ContentDiffer.between(current.content(), command.content());
            if (!changes.isEmpty()) {
                VersionBump bump = VersionBumpPolicy.determine(current.content(), command.content());
                SemVer next = SemVer.parse(standard.getCurrentVersion()).bump(bump.type());
                DataStandardVersion version = new DataStandardVersion(id, next.value(), bump.type(),
                        command.content(), String.join("; ", bump.reasons()));
                version.setTenantId(tenantId);
                version.stamp(digest.digest(command.content()), current.getContentHash(), changeContext.author(),
                        changeContext.messageOr(() -> String.join("; ", bump.reasons())));
                version = versions.save(version);
                standard.pointToVersion(version.getId(), version.getVersion());
            }
            if (command.category() != null) {
                standard.setCategory(command.category());
            }
            return toView(standard);
        });
    }

    /**
     * Rolls a standard back to an earlier version by writing a <em>new</em> version
     * whose content equals the target and whose change class is derived from the
     * actual diff. History is preserved.
     */
    public StandardView rollback(String tenantId, Long id, String targetVersion) {
        return tenantScope.call(tenantId, () -> {
            DataStandard standard = requireStandard(tenantId, id);
            DataStandardVersion current = requireVersion(tenantId, id, standard.getCurrentVersion());
            DataStandardVersion target = requireVersion(tenantId, id, targetVersion);

            StandardContent restored = target.content();
            String restoredHash = digest.digest(restored);
            if (restoredHash.equals(current.getContentHash())) {
                return toView(standard);
            }
            VersionBump bump = VersionBumpPolicy.determine(current.content(), restored);
            SemVer next = SemVer.parse(standard.getCurrentVersion()).bump(bump.type());

            DataStandardVersion version = new DataStandardVersion(id, next.value(), bump.type(), restored,
                    "rollback to " + targetVersion + " (" + String.join("; ", bump.reasons()) + ")");
            version.setTenantId(tenantId);
            version.markSource(target.getSourceEnvironmentId(), target.getVersion());
            version.stamp(restoredHash, current.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> "rollback to " + targetVersion));
            version = versions.save(version);

            standard.pointToVersion(version.getId(), version.getVersion());
            return toView(standard);
        });
    }

    public StandardView transition(String tenantId, Long id, StandardStatus target) {
        return tenantScope.call(tenantId, () -> {
            DataStandard standard = requireStandard(tenantId, id);
            if (target == StandardStatus.PUBLISHED) {
                separationOfDuties.requireFourEyes(
                        requireVersion(tenantId, id, standard.getCurrentVersion()).getAuthor(),
                        "data standard '" + standard.getCode() + "'");
            }
            standard.transitionTo(target);
            return toView(standard);
        });
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(String tenantId, Long id) {
        requireStandard(tenantId, id);
        return tenantScope.call(tenantId, () -> versions
                .findByTenantIdAndStandardIdOrderByIdAsc(tenantId, id).stream()
                .map(DataStandardService::toVersionView).toList());
    }

    /** Recomputes the content hash to detect tampering (content-hash / integrity axis). */
    @Transactional(readOnly = true)
    public boolean verify(String tenantId, Long id, String version) {
        return tenantScope.call(tenantId, () -> {
            DataStandardVersion snapshot = requireVersion(tenantId, id, version);
            return digest.verify(snapshot.getContentHash(), snapshot.content());
        });
    }

    @Transactional(readOnly = true)
    public DiffView diff(String tenantId, Long id, String fromVersion, String toVersion) {
        return tenantScope.call(tenantId, () -> {
            DataStandardVersion from = requireVersion(tenantId, id, fromVersion);
            DataStandardVersion to = requireVersion(tenantId, id, toVersion);
            List<FieldChange> changes = ContentDiffer.between(from.content(), to.content());
            VersionChangeType type = changes.isEmpty()
                    ? VersionChangeType.PATCH
                    : VersionBumpPolicy.determine(from.content(), to.content()).type();
            return new DiffView(fromVersion, toVersion, type, changes);
        });
    }

    /** @return the artifacts that reference this standard (where-used). */
    @Transactional(readOnly = true)
    public List<String> references(String tenantId, Long id) {
        requireStandard(tenantId, id);
        return tenantScope.call(tenantId, () -> referenceCheckers.stream()
                .sorted(Comparator.comparingInt(ReferenceChecker::order))
                .flatMap(checker -> checker.findReferences(tenantId, id).stream())
                .toList());
    }

    /**
     * Logically deletes a standard when nothing references it (reference check).
     *
     * <p>Retention, not removal: the row and its immutable version history are kept and
     * only marked deleted, so the record stays retrievable for its retention period. The
     * change is captured in the audit trail; nothing is physically deleted.
     */
    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> {
            DataStandard standard = requireStandard(tenantId, id);
            List<String> references = referenceCheckers.stream()
                    .sorted(Comparator.comparingInt(ReferenceChecker::order))
                    .flatMap(checker -> checker.findReferences(tenantId, id).stream())
                    .toList();
            if (!references.isEmpty()) {
                throw new ConflictException("data standard '" + standard.getCode()
                        + "' is still referenced by: " + String.join("; ", references));
            }
            standard.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete"));
        });
    }

    /**
     * Restores a logically-deleted standard. Fails with a conflict when a live record
     * already uses its business code — the partial unique index allows reuse after a
     * logical delete, so a restore can collide with a newer record.
     */
    public StandardView restore(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            DataStandard standard = standards.findByTenantIdAndIdIncludingDeleted(tenantId, id)
                    .orElseThrow(() -> ResourceNotFoundException.of("DataStandard", id));
            if (standard.isDeleted()) {
                standard.restore();
                try {
                    standards.flush();
                } catch (DataIntegrityViolationException ex) {
                    throw new ConflictException("cannot restore data standard '" + standard.getCode()
                            + "': a live record already uses that code");
                }
            }
            return toView(standard);
        });
    }

    @Transactional(readOnly = true)
    public List<StandardExportItem> export(String tenantId, Long environmentId, Long applicationId) {
        return tenantScope.call(tenantId, () -> {
            List<DataStandard> rows = standards.search(tenantId, environmentId, applicationId, null, null);
            Map<Long, DataStandardVersion> current = currentVersions(tenantId, rows);
            List<StandardExportItem> items = new ArrayList<>(rows.size());
            for (DataStandard standard : rows) {
                DataStandardVersion version = currentVersionOf(tenantId, standard, current);
                items.add(new StandardExportItem(standard.getCode(), standard.getCategory(), version.content()));
            }
            return items;
        });
    }

    public ImportResult importItems(String tenantId, Long environmentId, Long applicationId,
            List<StandardExportItem> items) {
        return tenantScope.call(tenantId, () -> {
            int created = 0;
            int updated = 0;
            for (StandardExportItem item : items) {
                Optional<DataStandard> existing = standards
                        .findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                                tenantId, environmentId, applicationId, item.code());
                if (existing.isPresent()) {
                    update(tenantId, existing.get().getId(), new UpdateCommand(item.category(), item.content()));
                    updated++;
                } else {
                    create(tenantId, environmentId, applicationId,
                            new CreateCommand(item.code(), item.category(), item.content()));
                    created++;
                }
            }
            return new ImportResult(created, updated);
        });
    }

    // ------------------------------------------------------------------ helpers

    private DataStandard requireStandard(String tenantId, Long id) {
        return standards.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandard", id));
    }

    private DataStandardVersion requireVersion(String tenantId, Long standardId, String version) {
        return versions.findByTenantIdAndStandardIdAndVersion(tenantId, standardId, version)
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandardVersion", "version", version));
    }

    private static void validateContent(StandardContent content) {
        if (content == null) {
            throw new ValidationException("content is required");
        }
        if (content.name() == null || content.name().isBlank()) {
            throw new ValidationException("standard name is required");
        }
        if (content.dataType() == null || content.dataType().isBlank()) {
            throw new ValidationException("standard dataType is required");
        }
        if (content.length() != null && content.length() < 0) {
            throw new ValidationException("length must be non-negative");
        }
        if (content.scale() != null && content.scale() < 0) {
            throw new ValidationException("scale must be non-negative");
        }
    }

    /** Batch-loads the current snapshots for a page of standards (one query). */
    private Map<Long, DataStandardVersion> currentVersions(String tenantId, Collection<DataStandard> rows) {
        Set<Long> versionIds = rows.stream()
                .map(DataStandard::getCurrentVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return versions.findByTenantIdAndIdIn(tenantId, versionIds).stream()
                .collect(Collectors.toMap(DataStandardVersion::getId, Function.identity()));
    }

    /** Falls back to a point lookup only when the batch did not carry the row. */
    private DataStandardVersion currentVersionOf(String tenantId, DataStandard standard,
            Map<Long, DataStandardVersion> current) {
        DataStandardVersion version = current.get(standard.getCurrentVersionId());
        return version != null ? version
                : requireVersion(tenantId, standard.getId(), standard.getCurrentVersion());
    }

    private StandardView toView(DataStandard standard) {
        DataStandardVersion current = requireVersion(standard.getTenantId(), standard.getId(),
                standard.getCurrentVersion());
        return toView(standard, current);
    }

    private StandardView toView(DataStandard standard, DataStandardVersion current) {
        return new StandardView(standard.getId(), standard.getTenantId(), standard.getEnvironmentId(),
                standard.getApplicationId(), standard.getCode(), standard.getCategory(), standard.getStatus(),
                standard.getCurrentVersion(), current.content(), standard.getCreatedAt(), standard.getUpdatedAt());
    }

    private static StandardSummaryView toSummary(DataStandard standard, DataStandardVersion current) {
        return new StandardSummaryView(standard.getId(), standard.getEnvironmentId(), standard.getApplicationId(),
                standard.getCode(), current == null ? null : current.getName(), standard.getCategory(),
                current == null ? null : current.getDataType(), standard.getStatus(),
                standard.getCurrentVersion(), standard.getUpdatedAt());
    }

    private static VersionView toVersionView(DataStandardVersion version) {
        return new VersionView(version.getId(), version.getVersion(), version.getChangeType(),
                version.getChangeSummary(), version.content(), version.getSourceEnvironmentId(),
                version.getSourceVersion(), version.getCreatedAt(), version.getContentHash(), version.getParentHash(),
                version.getAuthor(), version.getMessage());
    }
}
