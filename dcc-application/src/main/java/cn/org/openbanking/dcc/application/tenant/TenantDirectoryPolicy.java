package cn.org.openbanking.dcc.application.tenant;

import java.util.Set;

/**
 * The application-owned face of the authorization directory. The security module's
 * {@code DirectoryAccessPolicy} seam is mirrored here because {@code dcc-application}
 * stays protocol-agnostic and must not depend on transport/security types; every
 * exposure layer (dcc-web, dcc-mcp) adapts its beans onto the seam at wire time.
 *
 * <p>Semantics are deliberately narrow see ADR 0003:
 * {@link #isMember(String, String)} gates (no live membership in the claimed tenant
 * rejects the request outright); {@link #assignedRoles(String, String)} adds (unioned
 * with the token's claim roles, never overriding what the IdP grants).
 */
public interface TenantDirectoryPolicy {

    /** @return true when the subject may act inside the tenant (live membership). */
    boolean isMember(String subject, String tenantId);

    /** @return tenant-wide roles granted to the subject (never null). */
    Set<String> assignedRoles(String subject, String tenantId);

    Set<String> assignedRoles(String subject, String tenantId, String scopeType, String scopeCode, Long targetId);
}
