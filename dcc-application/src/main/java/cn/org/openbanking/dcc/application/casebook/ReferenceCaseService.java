package cn.org.openbanking.dcc.application.casebook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.SnapshotCodec;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.application.tenant.TenantApplicationService;
import cn.org.openbanking.dcc.application.tenant.TenantEnvironmentService;
import cn.org.openbanking.dcc.core.casebook.ReferenceCase;
import cn.org.openbanking.dcc.core.casebook.ReferenceCaseAdoption;
import cn.org.openbanking.dcc.core.casebook.ReferenceCaseFeature;
import cn.org.openbanking.dcc.core.casebook.repository.ReferenceCaseAdoptionRepository;
import cn.org.openbanking.dcc.core.casebook.repository.ReferenceCaseRepository;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.template.content.TemplateContent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Reference-case library (指导性案例, ADR 0005 §D7).
 *
 * <p>Two audiences, two halves of this service:
 * <ul>
 *   <li><b>Platform administrator</b> — authors, updates, disables and logically deletes cases.
 *       Cases belong to no tenant, so this half never touches tenant data.</li>
 *   <li><b>Tenant administrator</b> — browses the library and <em>adopts</em> a case into its own
 *       tenant, which creates a <em>draft</em> artifact (or the environment/application skeleton)
 *       and records the provenance in the immutable version message.</li>
 * </ul>
 *
 * <p>Adoption is one-way and manual: editing a case afterwards never rewrites what a tenant
 * already adopted — the platform cannot reach into tenant content, by design.
 */
@Service
@Transactional
public class ReferenceCaseService {

    public record CreateCommand(
            @NotNull ReferenceCaseFeature feature,
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1024) String description,
            @NotBlank String content,
            @Size(max = 32) String caseVersion,
            Boolean enabled) {
    }

    public record UpdateCommand(
            @Size(max = 255) String name,
            @Size(max = 1024) String description,
            String content,
            @Size(max = 32) String caseVersion,
            Boolean enabled) {
    }

    /**
     * Adopt a case into the caller's tenant.
     *
     * @param environmentId target environment (content features only)
     * @param applicationId target application (content features only)
     * @param templateId    template the adopted interface uses (INTERFACE feature only)
     * @param code          optional business code override, so a case can be adopted repeatedly
     */
    public record AdoptCommand(Long environmentId, Long applicationId, Long templateId,
            @Size(max = 128) String code) {
    }

    public record CaseView(Long id, ReferenceCaseFeature feature, String code, String name, String description,
            String content, String caseVersion, boolean enabled) {
    }

    public record AdoptResult(String feature, String adoptedFrom, List<String> created) {
    }

    /**
     * One adoption as seen by the tenant that adopted it (ADR 0005 §D7 follow-up).
     *
     * @param stale whether the case has since moved to a newer version — the copy does not follow
     *              automatically, it only gets flagged
     */
    public record CaseAdoptionView(Long id, Long caseId, String caseCode, String adoptedCaseVersion,
            String currentCaseVersion, boolean stale, ReferenceCaseFeature artifactType, Long artifactId,
            String artifactCode, String adoptedBy, Instant adoptedAt) {
    }

    /**
     * One adoption as seen by the platform administrator: which tenant adopted which case version.
     * Deliberately carries no artifact id/code — the platform does not read tenant business data.
     */
    public record CaseReachView(String tenantId, String adoptedCaseVersion, String currentCaseVersion,
            boolean stale, String adoptedBy, Instant adoptedAt) {
    }

    /** What an adoption created: one artifact id/code (absent for a skeleton) plus display labels. */
    private record Created(ReferenceCaseFeature type, Long artifactId, String artifactCode, List<String> labels) {
    }

    /** The {@code SKELETON} payload: the recommended environment/application shape. */
    public record SkeletonPayload(List<EnvironmentSpec> environments, List<ApplicationSpec> applications) {
    }

    public record EnvironmentSpec(String code, String name, String description, Integer sortOrder) {
    }

    public record ApplicationSpec(String code, String name, String description) {
    }

    private final ReferenceCaseRepository cases;
    private final ReferenceCaseAdoptionRepository adoptions;
    private final DataStandardService standards;
    private final TableStructureService tables;
    private final InterfaceService interfaces;
    private final TemplateService templates;
    private final TenantEnvironmentService environments;
    private final TenantApplicationService applications;
    private final SnapshotCodec codec;
    private final ObjectMapper objectMapper;
    private final ChangeContext changeContext;

    public ReferenceCaseService(ReferenceCaseRepository cases,
            ReferenceCaseAdoptionRepository adoptions,
            DataStandardService standards,
            TableStructureService tables,
            InterfaceService interfaces,
            TemplateService templates,
            TenantEnvironmentService environments,
            TenantApplicationService applications,
            SnapshotCodec codec,
            ObjectMapper objectMapper,
            ChangeContext changeContext) {
        this.cases = cases;
        this.adoptions = adoptions;
        this.standards = standards;
        this.tables = tables;
        this.interfaces = interfaces;
        this.templates = templates;
        this.environments = environments;
        this.applications = applications;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.changeContext = changeContext;
    }

    // ------------------------------------------------------------------ platform side

    @Transactional(readOnly = true)
    public List<CaseView> list(ReferenceCaseFeature feature, boolean adoptableOnly) {
        List<ReferenceCase> found = feature == null
                ? cases.findAllByOrderByIdDesc()
                : (adoptableOnly
                        ? cases.findByFeatureAndEnabledTrueOrderByIdAsc(feature)
                        : cases.findByFeatureOrderByIdAsc(feature));
        return found.stream().map(ReferenceCaseService::toView).toList();
    }

    @Transactional(readOnly = true)
    public CaseView get(Long id) {
        return toView(require(id));
    }

    public CaseView create(CreateCommand command) {
        cases.findByFeatureAndCode(command.feature(), command.code()).ifPresent(existing -> {
            throw new ValidationException("reference case already exists: " + command.feature()
                    + "/" + command.code());
        });
        ReferenceCase referenceCase = new ReferenceCase(command.feature(), command.code(), command.name(),
                command.description(), command.content(),
                command.caseVersion() == null || command.caseVersion().isBlank() ? "1.0.0" : command.caseVersion());
        referenceCase.setEnabled(command.enabled() == null || command.enabled());
        try {
            return toView(cases.saveAndFlush(referenceCase));
        } catch (DataIntegrityViolationException ex) {
            // partial unique index: a live case with this (feature, code) already exists
            throw new ValidationException("reference case already exists: " + command.feature()
                    + "/" + command.code());
        }
    }

    public CaseView update(Long id, UpdateCommand command) {
        ReferenceCase referenceCase = require(id);
        if (command.name() != null) {
            referenceCase.setName(command.name());
        }
        if (command.description() != null) {
            referenceCase.setDescription(command.description());
        }
        if (command.content() != null && !command.content().isBlank()) {
            referenceCase.setContent(command.content());
        }
        if (command.caseVersion() != null && !command.caseVersion().isBlank()) {
            referenceCase.setCaseVersion(command.caseVersion());
        }
        if (command.enabled() != null) {
            referenceCase.setEnabled(command.enabled());
        }
        return toView(referenceCase);
    }

    /** Logically deletes a case (retention, not removal). */
    public void delete(Long id) {
        require(id).markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete reference case"));
    }

    public CaseView restore(Long id) {
        ReferenceCase referenceCase = cases.findByIdIncludingDeleted(id)
                .orElseThrow(() -> ResourceNotFoundException.of("ReferenceCase", "id", id));
        if (referenceCase.isDeleted()) {
            referenceCase.restore();
        }
        return toView(referenceCase);
    }

    // ------------------------------------------------------------------ tenant side

    /**
     * Adopts a case into {@code tenantId}: the payload becomes a <em>draft</em> artifact (or the
     * environment/application skeleton) and the provenance is stamped into every version message.
     */
    public AdoptResult adopt(String tenantId, Long id, AdoptCommand command) {
        ReferenceCase referenceCase = require(id);
        if (!referenceCase.isEnabled()) {
            throw new ValidationException("reference case " + referenceCase.getCode() + " is not offered for adoption");
        }
        String provenance = "adopted from case " + referenceCase.getCode() + " v" + referenceCase.getCaseVersion();
        // captured before the message override: the adoption index records the real caller, and a
        // restore must not turn an unbound (system) flow into a bound one
        String adoptedBy = changeContext.boundAuthor();
        String previousMessage = changeContext.message();
        changeContext.set(adoptedBy, provenance);
        try {
            Created created = switch (referenceCase.getFeature()) {
                case DATA_STANDARD -> {
                    requireTarget(command);
                    var content = codec.read(referenceCase.getContent(), StandardContent.class);
                    var view = standards.create(tenantId, command.environmentId(), command.applicationId(),
                            new DataStandardService.CreateCommand(codeOr(command, referenceCase), null, content), true);
                    yield new Created(referenceCase.getFeature(), view.id(), view.code(),
                            List.of("data-standard:" + view.code() + "@" + view.currentVersion()));
                }
                case TABLE_STRUCTURE -> {
                    requireTarget(command);
                    var content = codec.read(referenceCase.getContent(), TableContent.class);
                    var view = tables.create(tenantId, command.environmentId(), command.applicationId(),
                            new TableStructureService.CreateCommand(codeOr(command, referenceCase), content));
                    yield new Created(referenceCase.getFeature(), view.id(), view.code(),
                            List.of("table-structure:" + view.code() + "@" + view.currentVersion()));
                }
                case INTERFACE -> {
                    requireTarget(command);
                    var content = codec.read(referenceCase.getContent(), InterfaceContent.class);
                    var view = interfaces.create(tenantId, command.environmentId(), command.applicationId(),
                            new InterfaceService.CreateCommand(codeOr(command, referenceCase), command.templateId(),
                                    content), true);
                    yield new Created(referenceCase.getFeature(), view.id(), view.interfaceNo(),
                            List.of("interface:" + view.interfaceNo() + "@" + view.currentVersion()));
                }
                case INTERFACE_TEMPLATE -> {
                    requireTarget(command);
                    var content = codec.read(referenceCase.getContent(), TemplateContent.class);
                    var view = templates.create(tenantId, command.environmentId(), command.applicationId(),
                            new TemplateService.CreateCommand(codeOr(command, referenceCase), content), true);
                    yield new Created(referenceCase.getFeature(), view.id(), view.code(),
                            List.of("interface-template:" + view.code() + "@" + view.currentVersion()));
                }
                case SKELETON -> new Created(referenceCase.getFeature(), null, null,
                        adoptSkeleton(tenantId, referenceCase));
            };
            // the machine-readable half of the provenance: "which of my objects came from an
            // outdated case?" cannot be answered from the version message alone
            adoptions.save(new ReferenceCaseAdoption(tenantId, referenceCase, created.type(), created.artifactId(),
                    created.artifactCode(), adoptedBy));
            return new AdoptResult(referenceCase.getFeature().name(), provenance, created.labels());
        } finally {
            if (adoptedBy == null) {
                changeContext.clear();
            } else {
                changeContext.set(adoptedBy, previousMessage);
            }
        }
    }

    private List<String> adoptSkeleton(String tenantId, ReferenceCase referenceCase) {
        SkeletonPayload payload = readSkeleton(referenceCase.getContent());
        List<String> created = new ArrayList<>();
        if (payload.environments() != null) {
            for (EnvironmentSpec spec : payload.environments()) {
                var environment = environments.create(tenantId, new TenantEnvironmentService.CreateCommand(
                        spec.code(), spec.name(), spec.description(), spec.sortOrder()));
                created.add("environment:" + environment.code());
            }
        }
        if (payload.applications() != null) {
            for (ApplicationSpec spec : payload.applications()) {
                var application = applications.create(tenantId, new TenantApplicationService.CreateCommand(
                        spec.code(), spec.name(), spec.description()));
                created.add("application:" + application.code());
            }
        }
        return created;
    }

    private SkeletonPayload readSkeleton(String content) {
        try {
            return objectMapper.readValue(content, SkeletonPayload.class);
        } catch (RuntimeException ex) {
            throw new ValidationException("reference case SKELETON content is not a valid payload: "
                    + ex.getMessage());
        }
    }

    private static void requireTarget(AdoptCommand command) {
        if (command == null || command.environmentId() == null || command.applicationId() == null) {
            throw new ValidationException("environmentId and applicationId are required to adopt this case");
        }
    }

    private static String codeOr(AdoptCommand command, ReferenceCase referenceCase) {
        return command.code() == null || command.code().isBlank() ? referenceCase.getCode() : command.code();
    }

    // ------------------------------------------------------------------ adoption index

    /**
     * @return this tenant's adoptions, newest first, each flagged when the case has moved on since
     *         the copy was taken. The copy is never rewritten automatically.
     */
    @Transactional(readOnly = true)
    public List<CaseAdoptionView> adoptionsOf(String tenantId) {
        List<ReferenceCaseAdoption> rows = adoptions.findByTenantIdOrderByIdDesc(tenantId);
        Map<Long, String> currentVersions = currentCaseVersions(rows);
        return rows.stream().map(row -> {
            String current = currentVersions.get(row.getCaseId());
            return new CaseAdoptionView(row.getId(), row.getCaseId(), row.getCaseCode(), row.getCaseVersion(),
                    current, current != null && !current.equals(row.getCaseVersion()), row.getArtifactType(),
                    row.getArtifactId(), row.getArtifactCode(), row.getAdoptedBy(), row.getCreatedAt());
        }).toList();
    }

    /**
     * @return every tenant's adoption of one case, newest first — the case author's reach view. No
     *         tenant artifact identifier is exposed: the platform sees reach, not tenant content.
     */
    @Transactional(readOnly = true)
    public List<CaseReachView> reachOf(Long caseId) {
        ReferenceCase referenceCase = require(caseId);
        String current = referenceCase.getCaseVersion();
        return adoptions.findByCaseIdOrderByIdDesc(caseId).stream()
                .map(row -> new CaseReachView(row.getTenantId(), row.getCaseVersion(), current,
                        !current.equals(row.getCaseVersion()), row.getAdoptedBy(), row.getCreatedAt()))
                .toList();
    }

    private Map<Long, String> currentCaseVersions(List<ReferenceCaseAdoption> rows) {
        Map<Long, String> versions = new HashMap<>();
        List<Long> ids = rows.stream().map(ReferenceCaseAdoption::getCaseId).distinct().toList();
        if (!ids.isEmpty()) {
            cases.findAllById(ids).forEach(found -> versions.put(found.getId(), found.getCaseVersion()));
        }
        return versions;
    }

    private ReferenceCase require(Long id) {
        return cases.findById(id).orElseThrow(() -> ResourceNotFoundException.of("ReferenceCase", "id", id));
    }

    private static CaseView toView(ReferenceCase referenceCase) {
        return new CaseView(referenceCase.getId(), referenceCase.getFeature(), referenceCase.getCode(),
                referenceCase.getName(), referenceCase.getDescription(), referenceCase.getContent(),
                referenceCase.getCaseVersion(), referenceCase.isEnabled());
    }
}
