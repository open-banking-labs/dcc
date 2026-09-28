package cn.org.openbanking.dcc.application.migration;

import java.time.Instant;
import java.util.List;

import cn.org.openbanking.dcc.application.common.MigrationContext;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.application.interfaceapi.InterfaceService;
import cn.org.openbanking.dcc.application.standard.DataStandardService;
import cn.org.openbanking.dcc.application.table.TableStructureService;
import cn.org.openbanking.dcc.application.template.TemplateService;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.interfaceapi.repository.InterfaceDefinitionRepository;
import cn.org.openbanking.dcc.core.migration.ArtifactType;
import cn.org.openbanking.dcc.core.migration.MigrationOrder;
import cn.org.openbanking.dcc.core.migration.MigrationStatus;
import cn.org.openbanking.dcc.core.migration.repository.MigrationOrderRepository;
import cn.org.openbanking.dcc.core.standard.repository.DataStandardRepository;
import cn.org.openbanking.dcc.core.table.repository.TableStructureRepository;
import cn.org.openbanking.dcc.core.template.repository.InterfaceTemplateRepository;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for environment migration (环境迁移) with approval:
 * 待提交 → 待审批 → 已批准 → 已迁移 (or 已拒绝 / 已回滚).
 *
 * <p>Execution copies the source artifact's current version into the target
 * environment by driving the target artifact service under a {@link MigrationContext}
 * (which bypasses the direct-maintenance guard), so a target environment's version
 * can only ever come from a migration.
 */
@Service
@Transactional
public class MigrationService {

    private final MigrationOrderRepository orders;
    private final MigrationContext context;
    private final TenantScope tenantScope;
    private final DataStandardService standardService;
    private final TableStructureService tableService;
    private final InterfaceService interfaceService;
    private final TemplateService templateService;
    private final DataStandardRepository standards;
    private final TableStructureRepository tables;
    private final InterfaceDefinitionRepository interfaces;
    private final InterfaceTemplateRepository templates;

    public MigrationService(MigrationOrderRepository orders,
            MigrationContext context,
            TenantScope tenantScope,
            DataStandardService standardService,
            TableStructureService tableService,
            InterfaceService interfaceService,
            TemplateService templateService,
            DataStandardRepository standards,
            TableStructureRepository tables,
            InterfaceDefinitionRepository interfaces,
            InterfaceTemplateRepository templates) {
        this.orders = orders;
        this.context = context;
        this.tenantScope = tenantScope;
        this.standardService = standardService;
        this.tableService = tableService;
        this.interfaceService = interfaceService;
        this.templateService = templateService;
        this.standards = standards;
        this.tables = tables;
        this.interfaces = interfaces;
        this.templates = templates;
    }

    public record CreateCommand(@NotNull ArtifactType artifactType, @NotNull Long artifactId,
            @NotNull Long sourceEnvironmentId, @NotNull Long targetEnvironmentId, String note) {
    }

    public record MigrationOrderView(Long id, String tenantId, Long sourceEnvironmentId, Long targetEnvironmentId,
            ArtifactType artifactType, Long artifactId, String artifactCode, String sourceVersion,
            MigrationStatus status, String note, Long targetArtifactId, String targetVersion,
            Instant createdAt, Instant updatedAt) {
    }

    /** A migration result: the target artifact id/version and its version before the migration. */
    private record MigrationResult(Long targetId, String targetVersion, String previousTargetVersion) {
    }

    public MigrationOrderView create(String tenantId, CreateCommand command) {
        return tenantScope.call(tenantId, () -> {
            if (command.sourceEnvironmentId().equals(command.targetEnvironmentId())) {
                throw new ValidationException("source and target environment must differ");
            }
            String code = artifactCode(tenantId, command.artifactType(), command.artifactId());
            MigrationOrder order = new MigrationOrder(command.sourceEnvironmentId(), command.targetEnvironmentId(),
                    command.artifactType(), command.artifactId(), code, MigrationStatus.PENDING_SUBMISSION);
            order.setTenantId(tenantId);
            order.setNote(command.note());
            return toView(orders.save(order));
        });
    }

    public MigrationOrderView submit(String tenantId, Long id) {
        return transition(tenantId, id, MigrationStatus.PENDING_APPROVAL);
    }

    public MigrationOrderView approve(String tenantId, Long id) {
        return transition(tenantId, id, MigrationStatus.APPROVED);
    }

    public MigrationOrderView reject(String tenantId, Long id) {
        return transition(tenantId, id, MigrationStatus.REJECTED);
    }

    public MigrationOrderView execute(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            MigrationOrder order = require(tenantId, id);
            order.transitionTo(MigrationStatus.MIGRATED);

            MigrationResult result = switch (order.getArtifactType()) {
                case DATA_STANDARD -> migrateDataStandard(tenantId, order);
                case TABLE_STRUCTURE -> migrateTable(tenantId, order);
                case INTERFACE -> migrateInterface(tenantId, order);
                case INTERFACE_TEMPLATE -> migrateTemplate(tenantId, order);
            };
            order.setSourceVersion(sourceVersion(tenantId, order));
            order.markMigrated(result.targetId(), result.targetVersion(), result.previousTargetVersion());
            return toView(order);
        });
    }

    public MigrationOrderView rollback(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            MigrationOrder order = require(tenantId, id);
            order.transitionTo(MigrationStatus.ROLLED_BACK);
            if (order.getTargetArtifactId() != null) {
                revertTarget(tenantId, order);
            }
            return toView(order);
        });
    }

    @Transactional(readOnly = true)
    public MigrationOrderView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> toView(require(tenantId, id)));
    }

    @Transactional(readOnly = true)
    public List<MigrationOrderView> search(String tenantId, Long targetEnvironmentId, MigrationStatus status) {
        return tenantScope.call(tenantId, () -> orders.search(tenantId, targetEnvironmentId, status).stream()
                .map(MigrationService::toView).toList());
    }

    // ------------------------------------------------------------------ per-type migration

    private MigrationResult migrateDataStandard(String tenantId, MigrationOrder order) {
        var source = standardService.get(tenantId, order.getArtifactId());
        Long targetEnv = order.getTargetEnvironmentId();
        Long appId = source.applicationId();
        var existing = standards.findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                tenantId, targetEnv, appId, source.code());
        String previous = existing.map(s -> s.getCurrentVersion()).orElse(null);
        var view = context.call(order.getSourceEnvironmentId(), source.currentVersion(), () -> existing.isPresent()
                ? standardService.update(tenantId, existing.get().getId(),
                        new DataStandardService.UpdateCommand(source.category(), source.content()))
                : standardService.create(tenantId, targetEnv, appId,
                        new DataStandardService.CreateCommand(source.code(), source.category(), source.content())));
        return new MigrationResult(view.id(), view.currentVersion(), previous);
    }

    private MigrationResult migrateTable(String tenantId, MigrationOrder order) {
        var source = tableService.get(tenantId, order.getArtifactId());
        Long targetEnv = order.getTargetEnvironmentId();
        Long appId = source.applicationId();
        var existing = tables.findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                tenantId, targetEnv, appId, source.code());
        String previous = existing.map(t -> t.getCurrentVersion()).orElse(null);
        var view = context.call(order.getSourceEnvironmentId(), source.currentVersion(), () -> existing.isPresent()
                ? tableService.update(tenantId, existing.get().getId(),
                        new TableStructureService.UpdateCommand(source.content()))
                : tableService.create(tenantId, targetEnv, appId,
                        new TableStructureService.CreateCommand(source.code(), source.content())));
        return new MigrationResult(view.id(), view.currentVersion(), previous);
    }

    private MigrationResult migrateInterface(String tenantId, MigrationOrder order) {
        var source = interfaceService.get(tenantId, order.getArtifactId());
        Long targetEnv = order.getTargetEnvironmentId();
        Long appId = source.applicationId();
        var existing = interfaces.findByTenantIdAndEnvironmentIdAndInterfaceNo(tenantId, targetEnv, source.interfaceNo());
        String previous = existing.map(i -> i.getCurrentVersion()).orElse(null);
        var view = context.call(order.getSourceEnvironmentId(), source.currentVersion(), () -> existing.isPresent()
                ? interfaceService.update(tenantId, existing.get().getId(),
                        new InterfaceService.UpdateCommand(source.templateId(), source.content()))
                : interfaceService.create(tenantId, targetEnv, appId,
                        new InterfaceService.CreateCommand(source.interfaceNo(), source.templateId(), source.content())));
        return new MigrationResult(view.id(), view.currentVersion(), previous);
    }

    private MigrationResult migrateTemplate(String tenantId, MigrationOrder order) {
        var source = templateService.get(tenantId, order.getArtifactId());
        Long targetEnv = order.getTargetEnvironmentId();
        Long appId = source.applicationId();
        var existing = templates.findByTenantIdAndEnvironmentIdAndApplicationIdAndCode(
                tenantId, targetEnv, appId, source.code());
        String previous = existing.map(t -> t.getCurrentVersion()).orElse(null);
        var view = context.call(order.getSourceEnvironmentId(), source.currentVersion(), () -> existing.isPresent()
                ? templateService.update(tenantId, existing.get().getId(),
                        new TemplateService.UpdateCommand(source.content()))
                : templateService.create(tenantId, targetEnv, appId,
                        new TemplateService.CreateCommand(source.code(), source.content())));
        return new MigrationResult(view.id(), view.currentVersion(), previous);
    }

    // ------------------------------------------------------------------ rollback of the target

    private void revertTarget(String tenantId, MigrationOrder order) {
        boolean created = order.getPreviousTargetVersion() == null;
        context.run(order.getSourceEnvironmentId(), order.getSourceVersion(), () -> {
            switch (order.getArtifactType()) {
                case DATA_STANDARD -> {
                    if (created) {
                        standardService.delete(tenantId, order.getTargetArtifactId());
                    } else {
                        standardService.rollback(tenantId, order.getTargetArtifactId(), order.getPreviousTargetVersion());
                    }
                }
                case TABLE_STRUCTURE -> {
                    if (created) {
                        tableService.delete(tenantId, order.getTargetArtifactId());
                    } else {
                        tableService.rollback(tenantId, order.getTargetArtifactId(), order.getPreviousTargetVersion());
                    }
                }
                case INTERFACE -> {
                    if (created) {
                        interfaceService.delete(tenantId, order.getTargetArtifactId());
                    } else {
                        interfaceService.rollback(tenantId, order.getTargetArtifactId(), order.getPreviousTargetVersion());
                    }
                }
                case INTERFACE_TEMPLATE -> {
                    if (created) {
                        templateService.delete(tenantId, order.getTargetArtifactId());
                    } else {
                        templateService.rollback(tenantId, order.getTargetArtifactId(), order.getPreviousTargetVersion());
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private String artifactCode(String tenantId, ArtifactType type, Long artifactId) {
        return switch (type) {
            case DATA_STANDARD -> standardService.get(tenantId, artifactId).code();
            case TABLE_STRUCTURE -> tableService.get(tenantId, artifactId).code();
            case INTERFACE -> interfaceService.get(tenantId, artifactId).interfaceNo();
            case INTERFACE_TEMPLATE -> templateService.get(tenantId, artifactId).code();
        };
    }

    private String sourceVersion(String tenantId, MigrationOrder order) {
        return switch (order.getArtifactType()) {
            case DATA_STANDARD -> standardService.get(tenantId, order.getArtifactId()).currentVersion();
            case TABLE_STRUCTURE -> tableService.get(tenantId, order.getArtifactId()).currentVersion();
            case INTERFACE -> interfaceService.get(tenantId, order.getArtifactId()).currentVersion();
            case INTERFACE_TEMPLATE -> templateService.get(tenantId, order.getArtifactId()).currentVersion();
        };
    }

    private MigrationOrderView transition(String tenantId, Long id, MigrationStatus target) {
        return tenantScope.call(tenantId, () -> {
            MigrationOrder order = require(tenantId, id);
            order.transitionTo(target);
            return toView(order);
        });
    }

    private MigrationOrder require(String tenantId, Long id) {
        return orders.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("MigrationOrder", id));
    }

    private static MigrationOrderView toView(MigrationOrder order) {
        return new MigrationOrderView(order.getId(), order.getTenantId(), order.getSourceEnvironmentId(),
                order.getTargetEnvironmentId(), order.getArtifactType(), order.getArtifactId(), order.getArtifactCode(),
                order.getSourceVersion(), order.getStatus(), order.getNote(), order.getTargetArtifactId(),
                order.getTargetVersion(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
