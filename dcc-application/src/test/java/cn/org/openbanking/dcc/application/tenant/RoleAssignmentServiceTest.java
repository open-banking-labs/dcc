package cn.org.openbanking.dcc.application.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import cn.org.openbanking.dcc.application.common.ChangeContext;
import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.tenant.RoleAssignment;
import cn.org.openbanking.dcc.core.tenant.ScopeType;
import cn.org.openbanking.dcc.core.tenant.repository.RoleAssignmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantEnvironmentRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for the directory role-assignment use cases (persistence mocked). */
class RoleAssignmentServiceTest {

    private final RoleAssignmentRepository assignments =
            org.mockito.Mockito.mock(RoleAssignmentRepository.class);
    private final TenantRepository tenants = org.mockito.Mockito.mock(TenantRepository.class);
    private final TenantEnvironmentRepository environments =
            org.mockito.Mockito.mock(TenantEnvironmentRepository.class);
    private final TenantApplicationRepository applications =
            org.mockito.Mockito.mock(TenantApplicationRepository.class);
    private final RoleAssignmentService service =
            new RoleAssignmentService(assignments, tenants, environments, applications,
                    new ChangeContext(new TenantProperties()));

    @Test
    void createGrantsARoleForAnActiveTenant() {
        when(tenants.existsByCodeAndEnabledTrue("t-a")).thenReturn(true);
        when(assignments.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, RoleAssignment.class));

        var view = service.create(new RoleAssignmentService.CreateCommand("user-1", "t-a", "DCC_ADMIN"));

        assertThat(view.subject()).isEqualTo("user-1");
        assertThat(view.tenantCode()).isEqualTo("t-a");
        assertThat(view.role()).isEqualTo("DCC_ADMIN");
        assertThat(view.scopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(view.scopeCode()).isNull();
    }

    @Test
    void createValidatesAndStoresAnEnvironmentScope() {
        when(tenants.existsByCodeAndEnabledTrue("t-a")).thenReturn(true);
        when(environments.existsByTenantIdAndCode("t-a", "DEV")).thenReturn(true);
        when(assignments.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, RoleAssignment.class));

        var view = service.create(new RoleAssignmentService.CreateCommand(
                "user-1", "t-a", "DCC_ADMIN", ScopeType.ENVIRONMENT, " DEV "));

        assertThat(view.scopeType()).isEqualTo(ScopeType.ENVIRONMENT);
        assertThat(view.scopeCode()).isEqualTo("DEV");
    }

    @Test
    void createMapsAUniqueIndexViolationToAConflict() {
        when(tenants.existsByCodeAndEnabledTrue("t-a")).thenReturn(true);
        when(assignments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(new RoleAssignmentService.CreateCommand("user-1", "t-a", "DCC_ADMIN")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteMarksTheRowDeletedInsteadOfRemovingIt() {
        RoleAssignment assignment = new RoleAssignment("user-1", "t-a", "DCC_ADMIN");
        when(assignments.findById(3L)).thenReturn(Optional.of(assignment));

        service.delete(3L);

        assertThat(assignment.isDeleted()).isTrue();
    }

    @Test
    void deleteOfAnUnknownIdIsNotFound() {
        when(assignments.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restoreConflictsWhenALiveTripleExists() {
        RoleAssignment assignment = new RoleAssignment("user-1", "t-a", "DCC_ADMIN");
        assignment.markDeleted("system", "revoke");
        when(assignments.findByIdIncludingDeleted(3L)).thenReturn(Optional.of(assignment));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("live duplicate"))
                .when(assignments).flush();

        assertThatThrownBy(() -> service.restore(3L)).isInstanceOf(ConflictException.class);
        verify(assignments).flush();
    }
}
