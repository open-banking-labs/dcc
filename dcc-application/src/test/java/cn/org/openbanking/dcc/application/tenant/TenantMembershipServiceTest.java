package cn.org.openbanking.dcc.application.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.TenantMembership;
import cn.org.openbanking.dcc.core.tenant.repository.TenantMembershipRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the directory membership use cases; persistence is mocked, no
 * infrastructure needed. The Spring wiring and database semantics are covered by the
 * dcc-web integration tests.
 */
class TenantMembershipServiceTest {

    private final TenantMembershipRepository memberships =
            org.mockito.Mockito.mock(TenantMembershipRepository.class);
    private final TenantRepository tenants = org.mockito.Mockito.mock(TenantRepository.class);
    private final TenantMembershipService service =
            new TenantMembershipService(memberships, tenants, new ChangeContext(new TenantProperties()));

    @Test
    void createGrantsAMembershipForAnActiveTenant() {
        when(tenants.existsByCodeAndEnabledTrue("t-a")).thenReturn(true);
        when(memberships.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, TenantMembership.class));

        var view = service.create(new TenantMembershipService.CreateCommand("user-1", "t-a"));

        assertThat(view.subject()).isEqualTo("user-1");
        assertThat(view.tenantCode()).isEqualTo("t-a");
    }

    @Test
    void createRejectsAnInactiveOrUnknownTenant() {
        when(tenants.existsByCodeAndEnabledTrue("t-gone")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new TenantMembershipService.CreateCommand("user-1", "t-gone")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createMapsAUniqueIndexViolationToAConflict() {
        when(tenants.existsByCodeAndEnabledTrue("t-a")).thenReturn(true);
        when(memberships.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(new TenantMembershipService.CreateCommand("user-1", "t-a")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteMarksTheRowDeletedInsteadOfRemovingIt() {
        TenantMembership membership = new TenantMembership("user-1", "t-a");
        when(memberships.findById(7L)).thenReturn(Optional.of(membership));

        service.delete(7L);

        assertThat(membership.isDeleted()).isTrue();
        verify(memberships).findById(7L);
    }

    @Test
    void deleteOfAnUnknownIdIsNotFound() {
        when(memberships.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restoreBringsBackADeletedGrant() {
        TenantMembership membership = new TenantMembership("user-1", "t-a");
        membership.markDeleted("system", "revoke");
        when(memberships.findByIdIncludingDeleted(7L)).thenReturn(Optional.of(membership));

        var view = service.restore(7L);

        assertThat(view.subject()).isEqualTo("user-1");
        assertThat(membership.isDeleted()).isFalse();
    }

    @Test
    void restoreConflictsWhenALiveGrantExists() {
        TenantMembership membership = new TenantMembership("user-1", "t-a");
        membership.markDeleted("system", "revoke");
        when(memberships.findByIdIncludingDeleted(7L)).thenReturn(Optional.of(membership));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("live duplicate"))
                .when(memberships).flush();

        assertThatThrownBy(() -> service.restore(7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void membershipsOfReturnsLiveRowsOnly() {
        when(memberships.findAllBySubject("user-1"))
                .thenReturn(List.of(new TenantMembership("user-1", "t-a")));

        assertThat(service.membershipsOf("user-1")).extracting("tenantCode").containsExactly("t-a");
    }
}
