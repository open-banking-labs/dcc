package cn.org.openbanking.dcc.application.template;

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
import cn.org.openbanking.dcc.core.template.InterfaceTemplate;
import cn.org.openbanking.dcc.core.template.TemplateVersion;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;
import cn.org.openbanking.dcc.core.template.content.TemplateField;
import cn.org.openbanking.dcc.core.template.content.TemplateSection;
import cn.org.openbanking.dcc.core.template.content.TemplateSectionCode;
import cn.org.openbanking.dcc.core.template.diff.TemplateContentDiffer;
import cn.org.openbanking.dcc.core.template.repository.InterfaceTemplateRepository;
import cn.org.openbanking.dcc.core.template.repository.TemplateVersionRepository;
import cn.org.openbanking.dcc.core.template.version.TemplateVersionBumpPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for interface templates (接口模板): CRUD, versioning (history/diff/
 * rollback), lifecycle, layered Head/Body/Trailer JSON content, field-to-standard
 * references and reference recording. A template belongs to a tenant + environment
 * + application and is bound by interfaces through its id.
 */
@Service
@Transactional
public class TemplateService {

    private final InterfaceTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final DataStandardRepository standards;
    private final DataStandardVersionRepository standardVersions;
    private final TenantScope tenantScope;
    private final SnapshotCodec codec;
    private final ReferenceRecorder referenceRecorder;
    private final MigrationPolicy migrationPolicy;
    private final ContentDigest digest;
    private final ChangeContext changeContext;

    public TemplateService(InterfaceTemplateRepository templates,
            TemplateVersionRepository versions,
            DataStandardRepository standards,
            DataStandardVersionRepository standardVersions,
            TenantScope tenantScope,
            SnapshotCodec codec,
            ReferenceRecorder referenceRecorder,
            MigrationPolicy migrationPolicy,
            ContentDigest digest,
            ChangeContext changeContext) {
        this.templates = templates;
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

    public record CreateCommand(@NotBlank @Size(max = 128) String code, @NotNull TemplateContent content) {
    }

    public record UpdateCommand(@NotNull TemplateContent content) {
    }

    public record TemplateView(Long id, String tenantId, Long environmentId, Long applicationId, String code,
            StandardStatus status, String currentVersion, TemplateContent content, Instant createdAt,
            Instant updatedAt) {
    }

    public record VersionView(Long id, String version, VersionChangeType changeType, String changeSummary,
            TemplateContent content, Long sourceEnvironmentId, String sourceVersion, Instant createdAt,
            String contentHash, String parentHash, String author, String message) {
    }

    public record TemplateDiffView(String fromVersion, String toVersion, VersionChangeType changeType,
            List<FieldChange> changes) {
    }

    public TemplateView create(String tenantId, Long environmentId, Long applicationId, CreateCommand command) {
        migrationPolicy.requireDirectMaintenanceAllowed(tenantId, environmentId);
        return tenantScope.call(tenantId, () -> {
            TemplateContent content = resolveContent(tenantId, command.content());
            if (templates.existsByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                    tenantId, environmentId, applicationId, command.code())) {
                throw new ConflictException("interface template already exists: " + command.code());
            }
            InterfaceTemplate template = new InterfaceTemplate(environmentId, applicationId, command.code(),
                    StandardStatus.DRAFT, SemVer.INITIAL.value());
            template.setTenantId(tenantId);
            template = templates.save(template);

            TemplateVersion version = new TemplateVersion(template.getId(), SemVer.INITIAL.value(),
                    VersionChangeType.MAJOR, codec.write(content), "initial version");
            version.setTenantId(tenantId);
            version.stamp(digest.digest(content), null, changeContext.author(), "initial version");
            version = versions.save(version);

            template.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, template, content);
            return toView(template, content);
        });
    }

    @Transactional(readOnly = true)
    public TemplateView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            InterfaceTemplate template = require(tenantId, id);
            return toView(template, currentContent(tenantId, template));
        });
    }

    @Transactional(readOnly = true)
    public List<TemplateView> search(String tenantId, Long environmentId, Long applicationId, StandardStatus status) {
        return tenantScope.call(tenantId, () -> templates.search(tenantId, environmentId, applicationId, status).stream()
                .map(template -> toView(template, currentContent(tenantId, template)))
                .toList());
    }

    public TemplateView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            InterfaceTemplate template = require(tenantId, id);
            migrationPolicy.requireDirectMaintenanceAllowed(tenantId, template.getEnvironmentId());
            TemplateVersion current = requireVersion(tenantId, id, template.getCurrentVersion());
            TemplateContent oldContent = content(current);
            TemplateContent newContent = resolveContent(tenantId, command.content());

            String newHash = digest.digest(newContent);
            if (newHash.equals(current.getContentHash())) {
                return toView(template, newContent);
            }
            List<FieldChange> changes = TemplateContentDiffer.between(oldContent, newContent);
            VersionBump bump = TemplateVersionBumpPolicy.determine(changes);
            SemVer next = SemVer.parse(template.getCurrentVersion()).bump(bump.type());

            TemplateVersion version = new TemplateVersion(id, next.value(), bump.type(),
                    codec.write(newContent), String.join("; ", bump.reasons()));
            version.setTenantId(tenantId);
            version.stamp(newHash, current.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> String.join("; ", bump.reasons())));
            version = versions.save(version);

            template.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, template, newContent);
            return toView(template, newContent);
        });
    }

    public TemplateView rollback(String tenantId, Long id, String targetVersion) {
        return tenantScope.call(tenantId, () -> {
            InterfaceTemplate template = require(tenantId, id);
            TemplateVersion currentVersion = requireVersion(tenantId, id, template.getCurrentVersion());
            TemplateContent current = content(currentVersion);
            TemplateVersion target = requireVersion(tenantId, id, targetVersion);
            TemplateContent targetContent = content(target);

            String targetHash = digest.digest(targetContent);
            if (targetHash.equals(currentVersion.getContentHash())) {
                return toView(template, targetContent);
            }
            List<FieldChange> changes = TemplateContentDiffer.between(current, targetContent);
            VersionBump bump = TemplateVersionBumpPolicy.determine(changes);
            SemVer next = SemVer.parse(template.getCurrentVersion()).bump(bump.type());

            TemplateVersion version = new TemplateVersion(id, next.value(), bump.type(),
                    codec.write(targetContent), "rollback to " + targetVersion);
            version.setTenantId(tenantId);
            version.markSource(target.getSourceEnvironmentId(), targetVersion);
            version.stamp(targetHash, currentVersion.getContentHash(), changeContext.author(),
                    changeContext.messageOr(() -> "rollback to " + targetVersion));
            version = versions.save(version);

            template.pointToVersion(version.getId(), version.getVersion());
            recordReferences(tenantId, template, targetContent);
            return toView(template, targetContent);
        });
    }

    public TemplateView transition(String tenantId, Long id, StandardStatus target) {
        return tenantScope.call(tenantId, () -> {
            InterfaceTemplate template = require(tenantId, id);
            template.transitionTo(target);
            return toView(template, currentContent(tenantId, template));
        });
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(String tenantId, Long id) {
        require(tenantId, id);
        return tenantScope.call(tenantId, () -> versions
                .findByTenantIdAndInterfaceTemplateIdOrderByIdAsc(tenantId, id).stream()
                .map(this::toVersionView).toList());
    }

    /** Recomputes the content hash to detect tampering (content-hash / integrity axis). */
    @Transactional(readOnly = true)
    public boolean verify(String tenantId, Long id, String version) {
        return tenantScope.call(tenantId, () -> {
            TemplateVersion snapshot = requireVersion(tenantId, id, version);
            return digest.verify(snapshot.getContentHash(), content(snapshot));
        });
    }

    @Transactional(readOnly = true)
    public TemplateDiffView diff(String tenantId, Long id, String fromVersion, String toVersion) {
        return tenantScope.call(tenantId, () -> {
            TemplateContent from = content(requireVersion(tenantId, id, fromVersion));
            TemplateContent to = content(requireVersion(tenantId, id, toVersion));
            List<FieldChange> changes = TemplateContentDiffer.between(from, to);
            VersionChangeType type = changes.isEmpty()
                    ? VersionChangeType.PATCH
                    : TemplateVersionBumpPolicy.determine(changes).type();
            return new TemplateDiffView(fromVersion, toVersion, type, changes);
        });
    }

    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> {
            InterfaceTemplate template = require(tenantId, id);
            referenceRecorder.removeAll(tenantId, ReferenceType.INTERFACE_TEMPLATE, id);
            versions.deleteAll(versions.findByTenantIdAndInterfaceTemplateIdOrderByIdAsc(tenantId, id));
            templates.delete(template);
        });
    }

    // ------------------------------------------------------------------ helpers

    private TemplateContent resolveContent(String tenantId, TemplateContent content) {
        validate(content);
        List<TemplateSection> sections = new ArrayList<>();
        for (TemplateSection section : content.sections()) {
            sections.add(new TemplateSection(section.code(), section.name(), resolveFields(tenantId, section.fields())));
        }
        return new TemplateContent(content.name(), content.description(), sections);
    }

    private List<TemplateField> resolveFields(String tenantId, List<TemplateField> fields) {
        List<TemplateField> resolved = new ArrayList<>();
        for (TemplateField field : fields) {
            List<TemplateField> children = resolveFields(tenantId, field.children());
            if (field.standardId() != null) {
                StandardContent standard = standardContent(tenantId, field.standardId());
                resolved.add(new TemplateField(field.name(), field.standardId(), field.standardCode(),
                        standard.dataType(), standard.length(), standard.scale(), field.required(), field.role(),
                        children));
            } else {
                resolved.add(new TemplateField(field.name(), null, null, field.dataType(), field.length(),
                        field.scale(), field.required(), field.role(), children));
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

    private void recordReferences(String tenantId, InterfaceTemplate template, TemplateContent content) {
        List<Long> ids = new ArrayList<>();
        for (TemplateSection section : content.sections()) {
            collectStandardIds(section.fields(), ids);
        }
        referenceRecorder.replaceAll(tenantId, ReferenceType.INTERFACE_TEMPLATE, template.getId(), template.getCode(),
                ReferenceRecorder.distinct(ids));
    }

    private static void collectStandardIds(List<TemplateField> fields, List<Long> ids) {
        for (TemplateField field : fields) {
            if (field.standardId() != null) {
                ids.add(field.standardId());
            }
            collectStandardIds(field.children(), ids);
        }
    }

    private static void validate(TemplateContent content) {
        List<TemplateSectionCode> codes = new ArrayList<>();
        for (TemplateSection section : content.sections()) {
            if (codes.contains(section.code())) {
                throw new ValidationException("duplicate template section: " + section.code());
            }
            codes.add(section.code());
            validateFields("section." + section.code(), section.fields());
        }
    }

    private static void validateFields(String path, List<TemplateField> fields) {
        List<String> names = new ArrayList<>();
        for (TemplateField field : fields) {
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

    private InterfaceTemplate require(String tenantId, Long id) {
        return templates.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("InterfaceTemplate", id));
    }

    private TemplateVersion requireVersion(String tenantId, Long templateId, String version) {
        return versions.findByTenantIdAndInterfaceTemplateIdAndVersion(tenantId, templateId, version)
                .orElseThrow(() -> ResourceNotFoundException.of("TemplateVersion", "version", version));
    }

    private TemplateContent currentContent(String tenantId, InterfaceTemplate template) {
        return content(requireVersion(tenantId, template.getId(), template.getCurrentVersion()));
    }

    private TemplateContent content(TemplateVersion version) {
        return codec.read(version.getContentJson(), TemplateContent.class);
    }

    private TemplateView toView(InterfaceTemplate template, TemplateContent content) {
        return new TemplateView(template.getId(), template.getTenantId(), template.getEnvironmentId(),
                template.getApplicationId(), template.getCode(), template.getStatus(), template.getCurrentVersion(),
                content, template.getCreatedAt(), template.getUpdatedAt());
    }

    private VersionView toVersionView(TemplateVersion version) {
        return new VersionView(version.getId(), version.getVersion(), version.getChangeType(),
                version.getChangeSummary(), content(version), version.getSourceEnvironmentId(),
                version.getSourceVersion(), version.getCreatedAt(), version.getContentHash(), version.getParentHash(),
                version.getAuthor(), version.getMessage());
    }
}
