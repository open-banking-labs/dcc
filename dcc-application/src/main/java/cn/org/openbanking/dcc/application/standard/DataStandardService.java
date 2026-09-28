package cn.org.openbanking.dcc.application.standard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.ContentDigest;
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

    private final DataStandardRepository standards;
    private final DataStandardVersionRepository versions;
    private final TenantScope tenantScope;
    private final List<ReferenceChecker> referenceCheckers;
    private final MigrationPolicy migrationPolicy;
    private final ContentDigest digest;
    private final ChangeContext changeContext;

    public DataStandardService(DataStandardRepository standards,
            DataStandardVersionRepository versions,
            TenantScope tenantScope,
            List<ReferenceChecker> referenceCheckers,
            MigrationPolicy migrationPolicy,
            ContentDigest digest,
            ChangeContext changeContext) {
        this.standards = standards;
        this.versions = versions;
        this.tenantScope = tenantScope;
        this.referenceCheckers = referenceCheckers;
        this.migrationPolicy = migrationPolicy;
        this.digest = digest;
        this.changeContext = changeContext;
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

    public record VersionView(Long id, String version, VersionChangeType changeType, String changeSummary,
            StandardContent content, Long sourceEnvironmentId, String sourceVersion, Instant createdAt,
            String contentHash, String parentHash, String author, String message) {
    }

    public record DiffView(String fromVersion, String toVersion, VersionChangeType changeType,
            List<FieldChange> changes) {
    }

    public record ImportResult(int created, int updated) {
    }

    // ------------------------------------------------------------------ use cases

    public StandardView create(String tenantId, Long environmentId, Long applicationId, CreateCommand command) {
        migrationPolicy.requireDirectMaintenanceAllowed(tenantId, environmentId);
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
                    VersionChangeType.MAJOR, command.content(), "initial version");
            version.setTenantId(tenantId);
            version.stamp(digest.digest(command.content()), null, changeContext.author(), "initial version");
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
        return tenantScope.call(tenantId, () -> standards
                .search(tenantId, environmentId, applicationId, category, status).stream()
                .map(this::toView).toList());
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

    /** Deletes a standard only when nothing references it (reference check). */
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
            versions.deleteAll(versions.findByTenantIdAndStandardIdOrderByIdAsc(tenantId, id));
            standards.delete(standard);
        });
    }

    @Transactional(readOnly = true)
    public List<StandardExportItem> export(String tenantId, Long environmentId, Long applicationId) {
        return tenantScope.call(tenantId, () -> {
            List<StandardExportItem> items = new ArrayList<>();
            for (DataStandard standard : standards.search(tenantId, environmentId, applicationId, null, null)) {
                DataStandardVersion current = requireVersion(tenantId, standard.getId(), standard.getCurrentVersion());
                items.add(new StandardExportItem(standard.getCode(), standard.getCategory(), current.content()));
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

    private StandardView toView(DataStandard standard) {
        DataStandardVersion current = requireVersion(standard.getTenantId(), standard.getId(),
                standard.getCurrentVersion());
        return new StandardView(standard.getId(), standard.getTenantId(), standard.getEnvironmentId(),
                standard.getApplicationId(), standard.getCode(), standard.getCategory(), standard.getStatus(),
                standard.getCurrentVersion(), current.content(), standard.getCreatedAt(), standard.getUpdatedAt());
    }

    private static VersionView toVersionView(DataStandardVersion version) {
        return new VersionView(version.getId(), version.getVersion(), version.getChangeType(),
                version.getChangeSummary(), version.content(), version.getSourceEnvironmentId(),
                version.getSourceVersion(), version.getCreatedAt(), version.getContentHash(), version.getParentHash(),
                version.getAuthor(), version.getMessage());
    }
}
