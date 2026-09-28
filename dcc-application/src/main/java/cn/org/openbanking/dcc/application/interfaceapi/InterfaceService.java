package cn.org.openbanking.dcc.application.interfaceapi;

import java.time.Instant;
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
import cn.org.openbanking.dcc.core.interfaceapi.InterfaceDefinition;
import cn.org.openbanking.dcc.core.interfaceapi.InterfaceVersion;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.interfaceapi.diff.InterfaceContentDiffer;
import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceDefinitionRepository;
import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceVersionRepository;
import cn.org.openbanking.dcc.core.interfaceapi.version.InterfaceVersionBumpPolicy;
import cn.org.openbanking.dcc.core.reference.ReferenceType;
import cn.org.openbanking.dcc.core.standard.DataStandard;
import cn.org.openbanking.dcc.core.standard.DataStandardVersion;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.StandardStatus;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardVersionRepository;
import cn.org.openbanking.dcc.core.standard.version.SemVer;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for business interfaces (业务接口): CRUD, versioning (history/diff/
 * rollback), lifecycle, input/output referencing data standards (including nested
 * and multi-referenced fields) and reference recording. Interface numbers are
 * unique within a tenant + environment.
 */
@Service
@Transactional
public class InterfaceService {

    private final InterfaceDefinitionRepository interfaces;
    private final InterfaceVersionRepository versions;
    private final DataStandardRepository standards;
    private final DataStandardVersionRepository standardVersions;
    private final TenantScope tenantScope;
    private final SnapshotCodec codec;
    private final ReferenceRecorder referenceRecorder;
    private final MigrationPolicy migrationPolicy;
    private final ContentDigest digest;
    private final ChangeContext changeContext;

    public InterfaceService(InterfaceDefinitionRepository interfaces,
            InterfaceVersionRepository versions,
            DataStandardRepository standards,
            DataStandardVersionRepository standardVersions,
            TenantScope tenantScope,
            SnapshotCodec codec,
            ReferenceRecorder referenceRecorder,
            MigrationPolicy migrationPolicy,
            ContentDigest digest,
            ChangeContext changeContext) {
        this.interfaces = interfaces;
        this.versions = versions;
        this.standards = standards;
        this.standardVersions = standardVersions;
        this.tenantScope = tenantScope;
        this.codec = codec;
        this.referenceRecorder = referenceRecorder;
        this.migrationPolicy = migrationPolicy;
        this.digest = digest;
        this.changeContext = changeContext;
    }

    public record CreateCommand(@NotBlank @Size(max = 64) String interfaceNo, Long templateId,
            @NotNull InterfaceContent content) {
    }

    public record UpdateCommand(Long templateId, @NotNull InterfaceContent content) {
    }

    public record InterfaceView(Long id, String tenantId, Long environmentId, Long applicationId, String interfaceNo,
            Long templateId, StandardStatus status, String currentVersion, InterfaceContent content,
            Instant createdAt, Instant updatedAt) {
    }

    public record VersionView(Long id, String version, VersionChangeType changeType, String changeSummary,
            InterfaceContent content, Long sourceEnvironmentId, String sourceVersion, Instant createdAt,
            String contentHash, String parentHash, String author, String message) {
    }

    public record InterfaceDiffView(String fromVersion, String toVersion, VersionChangeType changeType,
            List<FieldChange> changes) {
    }

    public InterfaceView create(String tenantId, Long environmentId, Long applicationId, CreateCommand command) {
        migrationPolicy.requireDirectMaintenanceAllowed(tenantId, environmentId);
        return tenantScope.call(tenantId, () -> {
            InterfaceContent content = resolveContent(tenantId, command.content());
            // interface number is unique within tenant + environment
            if (interfaces.existsByTenantIdAndEnvironmentIdAndInterfaceNo(tenantId, environmentId, command.interfaceNo())) {
                throw new ConflictException("interface number already exists in this environment: " + command.interfaceNo());
            }
            InterfaceDefinition definition = new InterfaceDefinition(environmentId, applicationId,
                    command.interfaceNo(), command.templateId(), StandardStatus.DRAFT, SemVer.INITIAL.value());
            definition.setTenantId(tenantId);
            definition = interfaces.save(definition);

            InterfaceVersion version = new InterfaceVersion(definition.getId(), SemVer.INITIAL.value(),
                    VersionChangeType.MAJOR, codec.write(content), "initial version");
            version.setTenantId(tenantId);
            version.stamp(digest.digest(content), null, changeContext.author(), "initial version");
            version = versions.save(version);

            definition.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, definition, content);
            return toView(definition, content);
        });
    }

    @Transactional(readOnly = true)
    public InterfaceView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            InterfaceDefinition definition = require(tenantId, id);
            return toView(definition, currentContent(tenantId, definition));
        });
    }

    @Transactional(readOnly = true)
    public List<InterfaceView> search(String tenantId, Long environmentId, Long applicationId, StandardStatus status) {
        return tenantScope.call(tenantId, () -> interfaces.search(tenantId, environmentId, applicationId, status).stream()
                .map(definition -> toView(definition, currentContent(tenantId, definition)))
                .toList());
    }

    public InterfaceView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            InterfaceDefinition definition = require(tenantId, id);
            migrationPolicy.requireDirectMaintenanceAllowed(tenantId, definition.getEnvironmentId());
            InterfaceVersion current = requireVersion(tenantId, id, definition.getCurrentVersion());
            InterfaceContent oldContent = content(current);
            InterfaceContent newContent = resolveContent(tenantId, command.content());

            String newHash = digest.digest(newContent);
            if (!newHash.equals(current.getContentHash())) {
                List<FieldChange> changes = InterfaceContentDiffer.between(oldContent, newContent);
                VersionBump bump = InterfaceVersionBumpPolicy.determine(changes);
                SemVer next = SemVer.parse(definition.getCurrentVersion()).bump(bump.type());
                InterfaceVersion version = new InterfaceVersion(id, next.value(), bump.type(),
                        codec.write(newContent), String.join("; ", bump.reasons()));
                version.setTenantId(tenantId);
                version.stamp(newHash, current.getContentHash(), changeContext.author(),
                        changeContext.messageOr(() -> String.join("; ", bump.reasons())));
                version = versions.save(version);
                definition.pointToVersion(version.getId(), version.getVersion());
                recordReferences(tenantId, definition, newContent);
            }
            if (command.templateId() != null) {
                definition.setTemplateId(command.templateId());
            }
            return toView(definition, newContent);
        });
    }

    public InterfaceView rollback(String tenantId, Long id, String targetVersion) {
        return tenantScope.call(tenantId, () -> {
            InterfaceDefinition definition = require(tenantId, id);
            InterfaceVersion currentVersion = requireVersion(tenantId, id, definition.getCurrentVersion());
            InterfaceContent current = content(currentVersion);
            InterfaceVersion target = requireVersion(tenantId, id, targetVersion);
            InterfaceContent targetContent = content(target);

            String targetHash = digest.digest(targetContent);
            if (targetHash.equals(currentVersion.getContentHash())) {
                return toView(definition, targetContent);
            }
            List<FieldChange> changes = InterfaceContentDiffer.between(current, targetContent);
            VersionBump bump = InterfaceVersionBumpPolicy.determine(changes);
            SemVer next = SemVer.parse(definition.getCurrentVersion()).bump(bump.type());

            InterfaceVersion version = new InterfaceVersion(id, next.value(), bump.type(),
                    codec.write(targetContent), "rollback to " + targetVersion);
            version.setTenantId(tenantId);
            version.markSource(target.getSourceEnvironmentId(), targetVersion);
            version.stamp(targetHash, currentVersion.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> "rollback to " + targetVersion));
            version = versions.save(version);

            definition.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, definition, targetContent);
            return toView(definition, targetContent);
        });
    }

    public InterfaceView transition(String tenantId, Long id, StandardStatus target) {
        return tenantScope.call(tenantId, () -> {
            InterfaceDefinition definition = require(tenantId, id);
            definition.transitionTo(target);
            return toView(definition, currentContent(tenantId, definition));
        });
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(String tenantId, Long id) {
        require(tenantId, id);
        return tenantScope.call(tenantId, () -> versions
                .findByTenantIdAndInterfaceDefinitionIdOrderByIdAsc(tenantId, id).stream()
                .map(this::toVersionView).toList());
    }

    /** Recomputes the content hash to detect tampering (content-hash / integrity axis). */
    @Transactional(readOnly = true)
    public boolean verify(String tenantId, Long id, String version) {
        return tenantScope.call(tenantId, () -> {
            InterfaceVersion snapshot = requireVersion(tenantId, id, version);
            return digest.verify(snapshot.getContentHash(), content(snapshot));
        });
    }

    @Transactional(readOnly = true)
    public InterfaceDiffView diff(String tenantId, Long id, String fromVersion, String toVersion) {
        return tenantScope.call(tenantId, () -> {
            InterfaceContent from = content(requireVersion(tenantId, id, fromVersion));
            InterfaceContent to = content(requireVersion(tenantId, id, toVersion));
            List<FieldChange> changes = InterfaceContentDiffer.between(from, to);
            VersionChangeType type = changes.isEmpty()
                    ? VersionChangeType.PATCH
                    : InterfaceVersionBumpPolicy.determine(changes).type();
            return new InterfaceDiffView(fromVersion, toVersion, type, changes);
        });
    }

    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> {
            InterfaceDefinition definition = require(tenantId, id);
            referenceRecorder.removeAll(tenantId, ReferenceType.INTERFACE, id);
            versions.deleteAll(versions.findByTenantIdAndInterfaceDefinitionIdOrderByIdAsc(tenantId, id));
            interfaces.delete(definition);
        });
    }

    // ------------------------------------------------------------------ helpers

    private InterfaceContent resolveContent(String tenantId, InterfaceContent content) {
        validate(content);
        return new InterfaceContent(content.name(), content.businessModule(), content.url(), content.description(),
                content.shortName(), resolveFields(tenantId, content.input()), resolveFields(tenantId, content.output()));
    }

    private List<InterfaceField> resolveFields(String tenantId, List<InterfaceField> fields) {
        List<InterfaceField> resolved = new ArrayList<>();
        for (InterfaceField field : fields) {
            List<InterfaceField> children = resolveFields(tenantId, field.children());
            if (field.standardId() != null) {
                StandardContent standard = standardContent(tenantId, field.standardId());
                resolved.add(new InterfaceField(field.name(), field.standardId(), field.standardCode(),
                        standard.dataType(), standard.length(), standard.scale(), field.required(), field.list(),
                        children));
            } else {
                resolved.add(new InterfaceField(field.name(), null, null, field.dataType(), field.length(),
                        field.scale(), field.required(), field.list(), children));
            }
        }
        return resolved;
    }

    private StandardContent standardContent(String tenantId, Long standardId) {
        DataStandard standard = standards.findByTenantIdAndId(tenantId, standardId)
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandard", standardId));
        DataStandardVersion version = standardVersions
                .findByTenantIdAndStandardIdAndVersion(tenantId, standardId, standard.getCurrentVersion())
                .orElseThrow(() -> ResourceNotFoundException.of("DataStandardVersion", standard.getCurrentVersion()));
        return version.content();
    }

    private void recordReferences(String tenantId, InterfaceDefinition definition, InterfaceContent content) {
        List<Long> ids = new ArrayList<>();
        collectStandardIds(content.input(), ids);
        collectStandardIds(content.output(), ids);
        referenceRecorder.replaceAll(tenantId, ReferenceType.INTERFACE, definition.getId(), definition.getInterfaceNo(),
                ReferenceRecorder.distinct(ids));
    }

    private static void collectStandardIds(List<InterfaceField> fields, List<Long> ids) {
        for (InterfaceField field : fields) {
            if (field.standardId() != null) {
                ids.add(field.standardId());
            }
            collectStandardIds(field.children(), ids);
        }
    }

    private static void validate(InterfaceContent content) {
        validateFields("input", content.input());
        validateFields("output", content.output());
    }

    private static void validateFields(String path, List<InterfaceField> fields) {
        List<String> names = new ArrayList<>();
        for (InterfaceField field : fields) {
            if (field.name() == null || field.name().isBlank()) {
                throw new ValidationException("field name is required under " + path);
            }
            if (names.contains(field.name())) {
                throw new ValidationException("duplicate field " + field.name() + " under " + path);
            }
            names.add(field.name());
            validateFields(path + "." + field.name(), field.children());
        }
    }

    private InterfaceDefinition require(String tenantId, Long id) {
        return interfaces.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("InterfaceDefinition", id));
    }

    private InterfaceVersion requireVersion(String tenantId, Long interfaceId, String version) {
        return versions.findByTenantIdAndInterfaceDefinitionIdAndVersion(tenantId, interfaceId, version)
                .orElseThrow(() -> ResourceNotFoundException.of("InterfaceVersion", "version", version));
    }

    private InterfaceContent currentContent(String tenantId, InterfaceDefinition definition) {
        return content(requireVersion(tenantId, definition.getId(), definition.getCurrentVersion()));
    }

    private InterfaceContent content(InterfaceVersion version) {
        return codec.read(version.getContentJson(), InterfaceContent.class);
    }

    private InterfaceView toView(InterfaceDefinition definition, InterfaceContent content) {
        return new InterfaceView(definition.getId(), definition.getTenantId(), definition.getEnvironmentId(),
                definition.getApplicationId(), definition.getInterfaceNo(), definition.getTemplateId(),
                definition.getStatus(), definition.getCurrentVersion(), content,
                definition.getCreatedAt(), definition.getUpdatedAt());
    }

    private VersionView toVersionView(InterfaceVersion version) {
        return new VersionView(version.getId(), version.getVersion(), version.getChangeType(),
                version.getChangeSummary(), content(version), version.getSourceEnvironmentId(),
                version.getSourceVersion(), version.getCreatedAt(), version.getContentHash(), version.getParentHash(),
                version.getAuthor(), version.getMessage());
    }
}
