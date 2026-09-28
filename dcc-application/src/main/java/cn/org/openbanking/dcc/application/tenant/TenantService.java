package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.tenant.Tenant;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for managing tenants. Tenants are the isolation root, so these
 * operations are platform-level and not themselves tenant-scoped.
 */
@Service
@Transactional
public class TenantService {

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public record CreateCommand(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description) {
    }

    public record UpdateCommand(
            @Size(max = 128) String name,
            @Size(max = 512) String description,
            Boolean enabled) {
    }

    public record TenantView(Long id, String code, String name, String description, boolean enabled,
            Instant createdAt, Instant updatedAt) {
    }

    public TenantView create(CreateCommand command) {
        if (tenants.existsByCode(command.code())) {
            throw new ConflictException("tenant code already exists: " + command.code());
        }
        Tenant tenant = new Tenant(command.code(), command.name(), command.description());
        return toView(tenants.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<TenantView> list() {
        return tenants.findAll().stream().map(TenantService::toView).toList();
    }

    @Transactional(readOnly = true)
    public TenantView getByCode(String code) {
        return toView(require(code));
    }

    public TenantView update(String code, UpdateCommand command) {
        Tenant tenant = require(code);
        if (command.name() != null) {
            tenant.setName(command.name());
        }
        if (command.description() != null) {
            tenant.setDescription(command.description());
        }
        if (command.enabled() != null) {
            tenant.setEnabled(command.enabled());
        }
        return toView(tenant);
    }

    public void delete(String code) {
        tenants.delete(require(code));
    }

    private Tenant require(String code) {
        return tenants.findByCode(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Tenant", "code", code));
    }

    private static TenantView toView(Tenant tenant) {
        return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getDescription(),
                tenant.isEnabled(), tenant.getCreatedAt(), tenant.getUpdatedAt());
    }
}
