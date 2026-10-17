package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.tenant.Tenant;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final ChangeContext changeContext;

    public TenantService(TenantRepository tenants, ChangeContext changeContext) {
        this.tenants = tenants;
        this.changeContext = changeContext;
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

    /**
     * Logically deletes a tenant. Retention, not removal: the row is kept and only marked
     * deleted (platform-level, so the audit entry carries no tenant id).
     */
    public void delete(String code) {
        Tenant tenant = require(code);
        tenant.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete"));
    }

    /**
     * Restores a logically-deleted tenant. Fails with a conflict when a live tenant already
     * uses its code.
     */
    public TenantView restore(String code) {
        Tenant tenant = tenants.findByCodeIncludingDeleted(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Tenant", "code", code));
        if (tenant.isDeleted()) {
            tenant.restore();
            try {
                tenants.flush();
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("cannot restore tenant '" + code
                        + "': a live record already uses that code");
            }
        }
        return toView(tenant);
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
