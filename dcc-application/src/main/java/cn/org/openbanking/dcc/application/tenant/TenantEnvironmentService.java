package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.id.IdFactory;
import cn.org.openbanking.dcc.core.tenant.TenantEnvironment;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the custom environments inside a tenant (DEV / TEST / PROD). */
@Service
@Transactional
public class TenantEnvironmentService {

    private final TenantEnvironmentRepository environments;
    private final TenantScope tenantScope;
    private final ChangeContext changeContext;
    private final IdFactory idFactory;

    public TenantEnvironmentService(TenantEnvironmentRepository environments, TenantScope tenantScope,
            ChangeContext changeContext, IdFactory idFactory) {
        this.environments = environments;
        this.tenantScope = tenantScope;
        this.changeContext = changeContext;
        this.idFactory = idFactory;
    }

    public record CreateCommand(
            @Size(max = 32) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            Integer sortOrder) {
    }

    public record UpdateCommand(
            @Size(max = 128) String name,
            @Size(max = 512) String description,
            Integer sortOrder) {
    }

    public record EnvironmentView(Long id, String tenantId, String code, String name, String description,
            int sortOrder, Instant createdAt, Instant updatedAt) {
    }

    public EnvironmentView create(String tenantId, CreateCommand command) {
        return tenantScope.call(tenantId, () -> {
            String code = resolveCode(tenantId, command.code());
            TenantEnvironment environment = new TenantEnvironment(code, command.name(),
                    command.description(), command.sortOrder() == null ? 0 : command.sortOrder());
            environment.setTenantId(tenantId);
            return toView(environments.save(environment));
        });
    }

    private String resolveCode(String tenantId, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String code = requestedCode.trim();
            if (environments.existsByTenantIdAndCode(tenantId, code)) {
                throw new ConflictException("environment already exists in tenant: " + code);
            }
            return code;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = idFactory.nextEnvironmentCode(tenantId);
            if (!environments.existsByTenantIdAndCode(tenantId, code)) {
                return code;
            }
        }
        throw new ConflictException("unable to allocate an environment code in tenant " + tenantId);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentView> list(String tenantId) {
        return tenantScope.call(tenantId,
                () -> environments.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                        .map(TenantEnvironmentService::toView).toList());
    }

    @Transactional(readOnly = true)
    public EnvironmentView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> toView(require(tenantId, id)));
    }

    public EnvironmentView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            TenantEnvironment environment = require(tenantId, id);
            if (command.name() != null) {
                environment.setName(command.name());
            }
            if (command.description() != null) {
                environment.setDescription(command.description());
            }
            if (command.sortOrder() != null) {
                environment.setSortOrder(command.sortOrder());
            }
            return toView(environment);
        });
    }

    /** Logically deletes an environment (retention): the row is kept and only marked deleted. */
    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> {
            TenantEnvironment environment = require(tenantId, id);
            environment.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete"));
        });
    }

    /**
     * Restores a logically-deleted environment. Fails with a conflict when a live record
     * already uses its code.
     */
    public EnvironmentView restore(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> {
            TenantEnvironment environment = environments.findByTenantIdAndIdIncludingDeleted(tenantId, id)
                    .orElseThrow(() -> ResourceNotFoundException.of("TenantEnvironment", id));
            if (environment.isDeleted()) {
                environment.restore();
                try {
                    environments.flush();
                } catch (DataIntegrityViolationException ex) {
                    throw new ConflictException("cannot restore environment '" + environment.getCode()
                            + "': a live record already uses that code");
                }
            }
            return toView(environment);
        });
    }

    private TenantEnvironment require(String tenantId, Long id) {
        return environments.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("TenantEnvironment", id));
    }

    private static EnvironmentView toView(TenantEnvironment environment) {
        return new EnvironmentView(environment.getId(), environment.getTenantId(), environment.getCode(),
                environment.getName(), environment.getDescription(), environment.getSortOrder(),
                environment.getCreatedAt(), environment.getUpdatedAt());
    }
}
