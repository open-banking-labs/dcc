package cn.org.openbanking.dcc.mcp.tool;

import cn.org.openbanking.dcc.security.DccPermissions;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Explicit permission check for MCP tools.
 *
 * <p>MCP tools are invoked reflectively by the Spring AI runtime, so relying on
 * {@code @PreAuthorize} would depend on whether that invocation passes through the AOP proxy.
 * These checks are therefore explicit and deterministic — the same fail-closed reasoning as
 * {@code dcc-web}'s method security, without betting on interception.
 *
 * <p>Baseline read access to the MCP surface is enforced once per request by
 * {@link McpAuthorizationFilter}; this class carries the finer, tool-level requirements.
 */
@Component
public class McpAuthorizer {

    private final DccPermissions permissions;

    public McpAuthorizer(DccPermissions permissions) {
        this.permissions = permissions;
    }

    /** Requires {@code audit.business}: the audit trail exposes who changed what, and the images. */
    public void requireAuditBusiness() {
        require(permissions.getAuditBusiness());
    }

    /** Requires {@code generation.read}: read-only generated projections. */
    public void requireGenerationRead() {
        require(permissions.getGenerationRead());
    }

    private void require(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority::equals);
        if (!granted) {
            throw new AccessDeniedException("missing authority: " + authority);
        }
    }
}
