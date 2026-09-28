package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.TenantScope;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.tenant.TenantApplication;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the applications inside a tenant (公共基础 / 账务系统 / 客户系统). */
@Service
@Transactional
public class TenantApplicationService {

    private final TenantApplicationRepository applications;
    private final TenantScope tenantScope;

    public TenantApplicationService(TenantApplicationRepository applications, TenantScope tenantScope) {
        this.applications = applications;
        this.tenantScope = tenantScope;
    }

    public record CreateCommand(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description) {
    }

    public record UpdateCommand(
            @Size(max = 128) String name,
            @Size(max = 512) String description) {
    }

    public record ApplicationView(Long id, String tenantId, String code, String name, String description,
            Instant createdAt, Instant updatedAt) {
    }

    public ApplicationView create(String tenantId, CreateCommand command) {
        return tenantScope.call(tenantId, () -> {
            if (applications.existsByTenantIdAndCode(tenantId, command.code())) {
                throw new ConflictException("application already exists in tenant: " + command.code());
            }
            TenantApplication application = new TenantApplication(command.code(), command.name(), command.description());
            application.setTenantId(tenantId);
            return toView(applications.save(application));
        });
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list(String tenantId) {
        return tenantScope.call(tenantId,
                () -> applications.findByTenantIdOrderByNameAsc(tenantId).stream()
                        .map(TenantApplicationService::toView).toList());
    }

    @Transactional(readOnly = true)
    public ApplicationView get(String tenantId, Long id) {
        return tenantScope.call(tenantId, () -> toView(require(tenantId, id)));
    }

    public ApplicationView update(String tenantId, Long id, UpdateCommand command) {
        return tenantScope.call(tenantId, () -> {
            TenantApplication application = require(tenantId, id);
            if (command.name() != null) {
                application.setName(command.name());
            }
            if (command.description() != null) {
                application.setDescription(command.description());
            }
            return toView(application);
        });
    }

    public void delete(String tenantId, Long id) {
        tenantScope.run(tenantId, () -> applications.delete(require(tenantId, id)));
    }

    private TenantApplication require(String tenantId, Long id) {
        return applications.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("TenantApplication", id));
    }

    private static ApplicationView toView(TenantApplication application) {
        return new ApplicationView(application.getId(), application.getTenantId(), application.getCode(),
                application.getName(), application.getDescription(),
                application.getCreatedAt(), application.getUpdatedAt());
    }
}
