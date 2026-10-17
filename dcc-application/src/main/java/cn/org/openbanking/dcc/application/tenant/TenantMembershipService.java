package cn.org.openbanking.dcc.application.tenant;

import java.time.Instant;
import java.util.List;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.TenantMembership;
import cn.org.openbanking.dcc.core.tenant.repository.TenantMembershipRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for managing the authorization directory (memberships). A membership
 * grants a subject the permission to act inside one tenant; it gates access in
 * directory mode and is audited/retained like every other row.
 *
 * <p>Unlike the business flows this is not tenant-scoped: directory administrators
 * onboard subjects into any tenant, identified by explicit query parameters.
 */
@Service
@Transactional
public class TenantMembershipService {

    public record CreateCommand(
            @NotBlank @Size(max = 128) String subject,
            @NotBlank @Size(max = 64) String tenantCode) {
    }

    public record MembershipView(Long id, String subject, String tenantCode, Instant createdAt, Instant updatedAt) {
    }

    private final TenantMembershipRepository memberships;
    private final TenantRepository tenants;
    private final ChangeContext changeContext;

    public TenantMembershipService(TenantMembershipRepository memberships, TenantRepository tenants,
            ChangeContext changeContext) {
        this.memberships = memberships;
        this.tenants = tenants;
        this.changeContext = changeContext;
    }

    @Transactional
    public MembershipView create(CreateCommand command) {
        requireActiveTenant(command.tenantCode());
        try {
            return toView(memberships.saveAndFlush(new TenantMembership(command.subject(), command.tenantCode())));
        } catch (DataIntegrityViolationException ex) {
            // partial unique index: a live (tenant, subject) pair already exists
            throw new ConflictException("membership already exists: subject '" + command.subject()
                    + "' in tenant '" + command.tenantCode() + "'");
        }
    }

    @Transactional(readOnly = true)
    public Page<MembershipView> search(String subject, String tenantCode, Pageable pageable) {
        return memberships.search(subject, tenantCode, pageable).map(TenantMembershipService::toView);
    }

    /** @return the caller's live memberships, ordered by grant time (oldest first). */
    @Transactional(readOnly = true)
    public List<MembershipView> membershipsOf(String subject) {
        return memberships.findAllBySubject(subject).stream()
                .map(TenantMembershipService::toView)
                .toList();
    }

    /** Logically deletes a membership (retention, not removal). */
    @Transactional
    public void delete(Long id) {
        TenantMembership membership = memberships.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("TenantMembership", "id", id));
        membership.markDeleted(changeContext.author(), changeContext.messageOr(() -> "delete membership"));
    }

    /** Restores a logically-deleted membership; conflicts with a re-granted live row. */
    @Transactional
    public MembershipView restore(Long id) {
        TenantMembership membership = memberships.findByIdIncludingDeleted(id)
                .orElseThrow(() -> ResourceNotFoundException.of("TenantMembership", "id", id));
        if (membership.isDeleted()) {
            membership.restore();
            try {
                memberships.flush();
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("cannot restore membership: a live row already grants subject '"
                        + membership.getSubject() + "' in tenant '" + membership.getTenantCode() + "'");
            }
        }
        return toView(membership);
    }

    private void requireActiveTenant(String tenantCode) {
        if (!tenants.existsByCodeAndEnabledTrue(tenantCode)) {
            throw new ValidationException("tenant '" + tenantCode + "' is disabled or unknown");
        }
    }

    private static MembershipView toView(TenantMembership membership) {
        return new MembershipView(membership.getId(), membership.getSubject(), membership.getTenantCode(),
                membership.getCreatedAt(), membership.getUpdatedAt());
    }
}
