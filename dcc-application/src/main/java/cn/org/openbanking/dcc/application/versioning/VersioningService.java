package cn.org.openbanking.dcc.application.versioning;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.application.common.ContentDigest;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.diff.InterfaceContentDiffer;
import cn.org.openbanking.dcc.core.interfaceapi.version.InterfaceVersionBumpPolicy;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.standard.diff.ContentDiffer;
import cn.org.openbanking.dcc.core.standard.diff.FieldChange;
import cn.org.openbanking.dcc.core.standard.version.VersionBump;
import cn.org.openbanking.dcc.core.standard.version.VersionBumpPolicy;
import cn.org.openbanking.dcc.core.standard.version.VersionChangeType;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.diff.ColumnChange;
import cn.org.openbanking.dcc.core.table.diff.TableChangeSet;
import cn.org.openbanking.dcc.core.table.diff.TableContentDiffer;
import cn.org.openbanking.dcc.core.table.version.TableVersionBumpPolicy;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;
import cn.org.openbanking.dcc.core.template.diff.TemplateContentDiffer;
import cn.org.openbanking.dcc.core.template.version.TemplateVersionBumpPolicy;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The engineering surface of the versioning model, shared by every artifact type:
 * <ul>
 *   <li>{@code hash} - the content hash (identity) of a stored version or raw content;</li>
 *   <li>{@code diff} - a human-readable change list between two versions;</li>
 *   <li>{@code suggest} - an <em>advisory</em> SemVer bump level (the maintainer
 *       confirms the final number);</li>
 *   <li>{@code drift} - recomputes stored hashes to detect tampering or stale output.</li>
 * </ul>
 * Bump rules follow the project's conservative, major-biased policy.
 */
@Service
@Transactional(readOnly = true)
public class VersioningService {

    private final DataStandardService standardService;
    private final TableStructureService tableService;
    private final InterfaceService interfaceService;
    private final TemplateService templateService;
    private final ContentDigest digest;
    private final ObjectMapper objectMapper;
    private final TenantScope tenantScope;

    public VersioningService(DataStandardService standardService,
            TableStructureService tableService,
            InterfaceService interfaceService,
            TemplateService templateService,
            ContentDigest digest,
            ObjectMapper objectMapper,
            TenantScope tenantScope) {
        this.standardService = standardService;
        this.tableService = tableService;
        this.interfaceService = interfaceService;
        this.templateService = templateService;
        this.digest = digest;
        this.objectMapper = objectMapper;
        this.tenantScope = tenantScope;
    }

    /** Advisory SemVer bump suggestion; {@code level} is one of MAJOR/MINOR/PATCH/NONE. */
    public record BumpSuggestion(String level, List<String> reasons, List<FieldChange> changes) {
        public BumpSuggestion {
            reasons = List.copyOf(reasons);
            changes = List.copyOf(changes);
        }
    }

    /** A stored snapshot whose recomputed hash differs from the recorded one. */
    public record DriftFinding(String artifactType, String code, String version) {
    }

    private record Facts(String contentHash, Object content) {
    }

    private record Evaluation(VersionChangeType level, List<String> reasons, List<FieldChange> changes) {
    }

    /** @return the content hash (identity) of a stored version. */
    public String irHash(String tenantId, ArtifactType type, Long id, String version) {
        return tenantScope.call(tenantId, () -> facts(tenantId, type, id, version).contentHash());
    }

    /** @return the content hash of raw JSON content (the {@code dcc hash} command). */
    public String hashContent(ArtifactType type, String json) {
        Object content = switch (type) {
            case DATA_STANDARD -> objectMapper.readValue(json, StandardContent.class);
            case TABLE_STRUCTURE -> objectMapper.readValue(json, TableContent.class);
            case INTERFACE -> objectMapper.readValue(json, InterfaceContent.class);
            case INTERFACE_TEMPLATE -> objectMapper.readValue(json, TemplateContent.class);
        };
        return digest.digest(content);
    }

    /** @return the human-readable change list between two versions. */
    public List<FieldChange> diff(String tenantId, ArtifactType type, Long id, String from, String to) {
        return tenantScope.call(tenantId, () -> evaluate(type, tenantId, id, from, to).changes());
    }

    /** @return an advisory SemVer bump level for the change between two versions. */
    public BumpSuggestion suggest(String tenantId, ArtifactType type, Long id, String from, String to) {
        return tenantScope.call(tenantId, () -> {
            Evaluation evaluation = evaluate(type, tenantId, id, from, to);
            boolean unchanged = evaluation.level() == null;
            String level = unchanged ? "NONE" : evaluation.level().name();
            List<String> reasons = unchanged ? List.of("no semantic change") : evaluation.reasons();
            return new BumpSuggestion(level, reasons, evaluation.changes());
        });
    }

    /** @return the artifacts in scope whose stored snapshots no longer verify (drift). */
    public List<DriftFinding> drift(String tenantId, Long environmentId, Long applicationId) {
        List<DriftFinding> findings = new ArrayList<>();
        standardService.search(tenantId, environmentId, applicationId, null, null).forEach(s -> {
            if (!standardService.verify(tenantId, s.id(), s.currentVersion())) {
                findings.add(new DriftFinding("DATA_STANDARD", s.code(), s.currentVersion()));
            }
        });
        tableService.search(tenantId, environmentId, applicationId, null).forEach(t -> {
            if (!tableService.verify(tenantId, t.id(), t.currentVersion())) {
                findings.add(new DriftFinding("TABLE_STRUCTURE", t.code(), t.currentVersion()));
            }
        });
        interfaceService.search(tenantId, environmentId, applicationId, null).forEach(i -> {
            if (!interfaceService.verify(tenantId, i.id(), i.currentVersion())) {
                findings.add(new DriftFinding("INTERFACE", i.interfaceNo(), i.currentVersion()));
            }
        });
        templateService.search(tenantId, environmentId, applicationId, null).forEach(t -> {
            if (!templateService.verify(tenantId, t.id(), t.currentVersion())) {
                findings.add(new DriftFinding("INTERFACE_TEMPLATE", t.code(), t.currentVersion()));
            }
        });
        return findings;
    }

    // ------------------------------------------------------------------ helpers

    private Evaluation evaluate(ArtifactType type, String tenantId, Long id, String from, String to) {
        return switch (type) {
            case DATA_STANDARD -> {
                StandardContent before = (StandardContent) facts(tenantId, type, id, from).content();
                StandardContent after = (StandardContent) facts(tenantId, type, id, to).content();
                List<FieldChange> changes = ContentDiffer.between(before, after);
                if (changes.isEmpty()) {
                    yield new Evaluation(null, List.of(), changes);
                }
                VersionBump bump = VersionBumpPolicy.determine(before, after);
                yield new Evaluation(bump.type(), bump.reasons(), changes);
            }
            case TABLE_STRUCTURE -> {
                TableContent before = (TableContent) facts(tenantId, type, id, from).content();
                TableContent after = (TableContent) facts(tenantId, type, id, to).content();
                TableChangeSet changes = TableContentDiffer.between(before, after);
                if (changes.isEmpty()) {
                    yield new Evaluation(null, List.of(), List.of());
                }
                VersionBump bump = TableVersionBumpPolicy.determine(changes);
                yield new Evaluation(bump.type(), bump.reasons(), flatten(changes));
            }
            case INTERFACE -> {
                InterfaceContent before = (InterfaceContent) facts(tenantId, type, id, from).content();
                InterfaceContent after = (InterfaceContent) facts(tenantId, type, id, to).content();
                List<FieldChange> changes = InterfaceContentDiffer.between(before, after);
                if (changes.isEmpty()) {
                    yield new Evaluation(null, List.of(), changes);
                }
                VersionBump bump = InterfaceVersionBumpPolicy.determine(changes);
                yield new Evaluation(bump.type(), bump.reasons(), changes);
            }
            case INTERFACE_TEMPLATE -> {
                TemplateContent before = (TemplateContent) facts(tenantId, type, id, from).content();
                TemplateContent after = (TemplateContent) facts(tenantId, type, id, to).content();
                List<FieldChange> changes = TemplateContentDiffer.between(before, after);
                if (changes.isEmpty()) {
                    yield new Evaluation(null, List.of(), changes);
                }
                VersionBump bump = TemplateVersionBumpPolicy.determine(changes);
                yield new Evaluation(bump.type(), bump.reasons(), changes);
            }
        };
    }

    private Facts facts(String tenantId, ArtifactType type, Long id, String version) {
        return switch (type) {
            case DATA_STANDARD -> standardService.history(tenantId, id).stream()
                    .filter(v -> v.version().equals(version)).findFirst()
                    .map(v -> new Facts(v.contentHash(), v.content()))
                    .orElseThrow(() -> ResourceNotFoundException.of("DataStandardVersion", "version", version));
            case TABLE_STRUCTURE -> tableService.history(tenantId, id).stream()
                    .filter(v -> v.version().equals(version)).findFirst()
                    .map(v -> new Facts(v.contentHash(), v.content()))
                    .orElseThrow(() -> ResourceNotFoundException.of("TableStructureVersion", "version", version));
            case INTERFACE -> interfaceService.history(tenantId, id).stream()
                    .filter(v -> v.version().equals(version)).findFirst()
                    .map(v -> new Facts(v.contentHash(), v.content()))
                    .orElseThrow(() -> ResourceNotFoundException.of("InterfaceVersion", "version", version));
            case INTERFACE_TEMPLATE -> templateService.history(tenantId, id).stream()
                    .filter(v -> v.version().equals(version)).findFirst()
                    .map(v -> new Facts(v.contentHash(), v.content()))
                    .orElseThrow(() -> ResourceNotFoundException.of("TemplateVersion", "version", version));
        };
    }

    private List<FieldChange> flatten(TableChangeSet changes) {
        List<FieldChange> flattened = new ArrayList<>();
        for (ColumnChange column : changes.columns()) {
            switch (column.kind()) {
                case ADDED -> flattened.add(new FieldChange("column." + column.columnName(), null, column.columnName()));
                case REMOVED -> flattened.add(new FieldChange("column." + column.columnName(), column.columnName(), null));
                case MODIFIED -> column.fields().forEach(field -> flattened.add(new FieldChange(
                        "column." + column.columnName() + "." + field.field(), field.before(), field.after())));
            }
        }
        flattened.addAll(changes.tableFields());
        flattened.addAll(changes.indexFields());
        return flattened;
    }
}
