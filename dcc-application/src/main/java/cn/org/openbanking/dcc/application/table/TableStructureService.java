package cn.org.openbanking.dcc.application.table;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.ContentDigest;
import cn.org.openbanking.dcc.application.common.SnapshotCodec;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.migration.MigrationPolicy;
import cn.org.openbanking.dcc.application.reference.ReferenceRecorder;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.DataStandardVersion;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardVersionRepository;
import cn.org.openbanking.dcc.core.standard.version.SemVer;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.table.TableStructure;
import cn.org.openbanking.dcc.core.table.TableStructureVersion;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.diff.TableChangeSet;
import cn.org.openbanking.dcc.core.table.diff.TableContentDiffer;
import cn.org.openbanking.dcc.core.table.repository.TableStructureRepository;
import cn.org.openbanking.dcc.core.table.repository.TableStructureVersionRepository;
import cn.org.openbanking.dcc.core.table.version.TableVersionBumpPolicy;
import cn.org.openbanking.dcc.generator.ddl.TableDdl;
import cn.org.openbanking.dcc.generator.ddl.TableDdlGenerator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for table structures (表结构): CRUD, versioning (history/diff/rollback),
 * lifecycle, DDL / Flyway SQL generation and reference recording.
 *
 * <p>Columns that reference a data standard have their physical type resolved from
 * that standard's current version and copied into the snapshot, keeping each version
 * self-contained.
 */
@Service
@Transactional
public class TableStructureService {

    private static final DateTimeFormatter FLYWAY_VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault());

    private final TableStructureRepository tables;
    private final TableStructureVersionRepository versions;
    private final DataStandardRepository standards;
    private final DataStandardVersionRepository standardVersions;
    private final TenantScope tenantScope;
    private final SnapshotCodec codec;
    private final ReferenceRecorder referenceRecorder;
    private final TableDdlGenerator ddlGenerator;
    private final MigrationPolicy migrationPolicy;
    private final ContentDigest digest;
    private final ChangeContext changeContext;

    public TableStructureService(TableStructureRepository tables,
            TableStructureVersionRepository versions,
            DataStandardRepository standards,
            DataStandardVersionRepository standardVersions,
            TenantScope tenantScope,
            SnapshotCodec codec,
            ReferenceRecorder referenceRecorder,
            TableDdlGenerator ddlGenerator,
            MigrationPolicy migrationPolicy,
            ContentDigest digest,
            ChangeContext changeContext) {
        this.tables = tables;
        this.versions = versions;
        this.standards = standards;
        this.standardVersions = standardVersions;
        this.tenantScope = tenantScope;
        this.codec = codec;
        this.referenceRecorder = referenceRecorder;
        this.ddlGenerator = ddlGenerator;
        this.migrationPolicy = migrationPolicy;
        this.digest = digest;
        this.changeContext = changeContext;
    }

    public record CreateCommand(@NotBlank @Size(max = 128) String code, @NotNull TableContent content) {
    }

    public record UpdateCommand(@NotNull TableContent content) {
    }

    public record TableView(Long id, String tenantId, Long environmentId, Long applicationId, String code,
            StandardStatus status, String currentVersion, TableContent content, Instant createdAt, Instant updatedAt) {
    }

    public record VersionView(Long id, String version, VersionChangeType changeType, String changeSummary,
            TableContent content, Long sourceEnvironmentId, String sourceVersion, Instant createdAt,
            String contentHash, String parentHash, String author, String message) {
    }

    public record TableDiffView(String fromVersion, String toVersion, VersionChangeType changeType,
            TableChangeSet changes) {
    }

    public TableView create(String tenantId, Long environmentId, Long applicationId, CreateCommand command) {
        migrationPolicy.requireDirectMaintenanceAllowed(tenantId, environmentId);
        return tenantScope.call(tenantId, () -> {
            TableContent content = resolveContent(tenantId, command.content());
            if (tables.existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                    tenantId, environmentId, applicationId, command.code())) {
                throw new ConflictException("table structure already exists: " + command.code());
            }
            TableStructure table = new TableStructure(environmentId, applicationId, command.code(),
                    StandardStatus.DRAFT, SemVer.INITIAL.value());
            table.setTenantId(tenantId);
            table = tables.save(table);

            TableStructureVersion version = new TableStructureVersion(table.getId(), SemVer.INITIAL.value(),
                    VersionChangeType.MAJOR, codec.write(content), "initial version");
            version.setTenantId(tenantId);
            version.stamp(digest.digest(content), null, changeContext.author(), "initial version");
            version = versions.save(version);

            table.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, table, content);
            return toView(table, content);
        });
    }

    @Transactional(readOnly = true)
    public TableView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            return toView(table, currentContent(tenantId, table));
        });
    }

    @Transactional(readOnly = true)
    public List<TableView> search(String tenantId, Long environmentId, Long applicationId, StandardStatus status) {
        return tenantScope.call(tenantId, () -> tables.search(tenantId, environmentId, applicationId, status).stream()
                .map(table -> toView(table, currentContent(tenantId, table)))
                .toList());
    }

    public TableView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            migrationPolicy.requireDirectMaintenanceAllowed(tenantId, table.getEnvironmentId());
            TableStructureVersion current = requireVersion(tenantId, id, table.getCurrentVersion());
            TableContent oldContent = content(current);
            TableContent newContent = resolveContent(tenantId, command.content());

            String newHash = digest.digest(newContent);
            if (newHash.equals(current.getContentHash())) {
                return toView(table, newContent);
            }
            TableChangeSet changes = TableContentDiffer.between(oldContent, newContent);
            VersionBump bump = TableVersionBumpPolicy.determine(changes);
            SemVer next = SemVer.parse(table.getCurrentVersion()).bump(bump.type());

            TableStructureVersion version = new TableStructureVersion(id, next.value(), bump.type(),
                    codec.write(newContent), String.join("; ", bump.reasons()));
            version.setTenantId(tenantId);
            version.stamp(newHash, current.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> String.join("; ", bump.reasons())));
            version = versions.save(version);

            table.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, table, newContent);
            return toView(table, newContent);
        });
    }

    public TableView rollback(String tenantId, Long id, String targetVersion) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            TableStructureVersion currentVersion = requireVersion(tenantId, id, table.getCurrentVersion());
            TableContent current = content(currentVersion);
            TableStructureVersion targetVersionEntity = requireVersion(tenantId, id, targetVersion);
            TableContent target = content(targetVersionEntity);

            String targetHash = digest.digest(target);
            if (targetHash.equals(currentVersion.getContentHash())) {
                return toView(table, target);
            }
            TableChangeSet changes = TableContentDiffer.between(current, target);
            VersionBump bump = TableVersionBumpPolicy.determine(changes);
            SemVer next = SemVer.parse(table.getCurrentVersion()).bump(bump.type());

            TableStructureVersion version = new TableStructureVersion(id, next.value(), bump.type(),
                    codec.write(target), "rollback to " + targetVersion + " (" + String.join("; ", bump.reasons()) + ")");
            version.setTenantId(tenantId);
            version.markSource(targetVersionEntity.getSourceEnvironmentId(), targetVersion);
            version.stamp(targetHash, currentVersion.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> "rollback to " + targetVersion));
            version = versions.save(version);

            table.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, table, target);
            return toView(table, target);
        });
    }

    public TableView transition(String tenantId, Long id, StandardStatus target) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            table.transitionTo(target);
            return toView(table, currentContent(tenantId, table));
        });
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(String tenantId, Long id) {
        requireTable(tenantId, id);
        return tenantScope.call(tenantId, () -> versions
                .findByTenantIdAndTableStructureIdOrderByIdAsc(tenantId, id).stream()
                .map(this::toVersionView).toList());
    }

    /** Recomputes the content hash to detect tampering (content-hash / integrity axis). */
    @Transactional(readOnly = true)
    public boolean verify(String tenantId, Long id, String version) {
        return tenantScope.call(tenantId, () -> {
            TableStructureVersion snapshot = requireVersion(tenantId, id, version);
            return digest.verify(snapshot.getContentHash(), content(snapshot));
        });
    }

    @Transactional(readOnly = true)
    public TableDiffView diff(String tenantId, Long id, String fromVersion, String toVersion) {
        return tenantScope.call(tenantId, () -> {
            TableContent from = content(requireVersion(tenantId, id, fromVersion));
            TableContent to = content(requireVersion(tenantId, id, toVersion));
            TableChangeSet changes = TableContentDiffer.between(from, to);
            VersionChangeType type = changes.isEmpty()
                    ? VersionChangeType.PATCH
                    : TableVersionBumpPolicy.determine(changes).type();
            return new TableDiffView(fromVersion, toVersion, type, changes);
        });
    }

    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            referenceRecorder.removeAll(tenantId, ReferenceType.TABLE_STRUCTURE, id);
            versions.deleteAll(versions.findByTenantIdAndTableStructureIdOrderByIdAsc(tenantId, id));
            tables.delete(table);
        });
    }

    @Transactional(readOnly = true)
    public TableDdl createDdl(String tenantId, Long id, String schema) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            TableStructureVersion current = requireVersion(tenantId, id, table.getCurrentVersion());
            return ddlGenerator.generateCreate(FLYWAY_VERSION.format(Instant.now()), schema, table.getCode(),
                    content(current), current.getContentHash());
        });
    }

    @Transactional(readOnly = true)
    public TableDdl alterDdl(String tenantId, Long id, String fromVersion, String toVersion, String schema) {
        return tenantScope.call(tenantId, () -> {
            TableStructure table = requireTable(tenantId, id);
            TableStructureVersion to = requireVersion(tenantId, id, toVersion);
            return ddlGenerator.generateAlter(FLYWAY_VERSION.format(Instant.now()), schema, table.getCode(),
                    content(requireVersion(tenantId, id, fromVersion)), content(to), to.getContentHash());
        });
    }

    // ------------------------------------------------------------------ helpers

    private TableContent resolveContent(String tenantId, TableContent content) {
        validate(content);
        List<TableColumn> resolved = new ArrayList<>();
        for (TableColumn column : content.columns()) {
            if (column.standardId() != null) {
                StandardContent standard = standardContent(tenantId, column.standardId());
                resolved.add(new TableColumn(column.columnName(), column.standardId(), column.standardCode(),
                        standard.dataType(), standard.length(), standard.scale(),
                        column.nullable(), column.primaryKey(),
                        column.defaultValue() != null ? column.defaultValue() : standard.defaultValue(),
                        column.sortOrder(), column.comment()));
            } else {
                resolved.add(column);
            }
        }
        return new TableContent(content.name(), content.description(), content.shardKey(), resolved, content.indexes());
    }

    private StandardContent standardContent(String tenantId, Long standardId) {
        DataStandard standard = standards.findByTenantIdAndId(tenantId, standardId)
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandard", standardId));
        DataStandardVersion version = standardVersions
                .findByTenantIdAndStandardIdAndVersion(tenantId, standardId, standard.getCurrentVersion())
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandardVersion", standard.getCurrentVersion()));
        return version.content();
    }

    private void recordReferences(String tenantId, TableStructure table, TableContent content) {
        referenceRecorder.replaceAll(tenantId, ReferenceType.TABLE_STRUCTURE, table.getId(), table.getCode(),
                ReferenceRecorder.distinct(content.columns().stream().map(TableColumn::standardId).toList()));
    }

    private static void validate(TableContent content) {
        if (content.name() == null || content.name().isBlank()) {
            throw new ValidationException("table name is required");
        }
        if (content.columns().isEmpty()) {
            throw new ValidationException("a table must have at least one column");
        }
        List<String> names = new ArrayList<>();
        for (TableColumn column : content.columns()) {
            if (column.columnName() == null || column.columnName().isBlank()) {
                throw new ValidationException("column name is required");
            }
            if (names.contains(column.columnName())) {
                throw new ValidationException("duplicate column: " + column.columnName());
            }
            names.add(column.columnName());
        }
    }

    private TableStructure requireTable(String tenantId, Long id) {
        return tables.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("TableStructure", id));
    }

    private TableStructureVersion requireVersion(String tenantId, Long tableId, String version) {
        return versions.findByTenantIdAndTableStructureIdAndVersion(tenantId, tableId, version)
                .orElseThrow(() -> ResourceNotFoundException.of("TableStructureVersion", "version", version));
    }

    private TableContent currentContent(String tenantId, TableStructure table) {
        return content(requireVersion(tenantId, table.getId(), table.getCurrentVersion()));
    }

    private TableContent content(TableStructureVersion version) {
        return codec.read(version.getContentJson(), TableContent.class);
    }

    private TableView toView(TableStructure table, TableContent content) {
        return new TableView(table.getId(), table.getTenantId(), table.getEnvironmentId(), table.getApplicationId(),
                table.getCode(), table.getStatus(), table.getCurrentVersion(), content, table.getCreatedAt(),
                table.getUpdatedAt());
    }

    private VersionView toVersionView(TableStructureVersion version) {
        return new VersionView(version.getId(), version.getVersion(), version.getChangeType(),
                version.getChangeSummary(), content(version), version.getSourceEnvironmentId(),
                version.getSourceVersion(), version.getCreatedAt(), version.getContentHash(), version.getParentHash(),
                version.getAuthor(), version.getMessage());
    }
}
