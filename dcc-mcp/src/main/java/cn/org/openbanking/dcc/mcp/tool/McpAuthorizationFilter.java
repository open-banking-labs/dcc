package cn.org.openbanking.dcc.mcp.tool;

import java.io.IOException;

import cn.org.openbanking.dcc.security.DccPermissions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Baseline authorization for the MCP surface (ADR 0005).
 *
 * <p>The MCP endpoint speaks JSON-RPC, so the tool being called is not visible in the URL and
 * {@code @PreAuthorize} on a tool method cannot be relied on (see {@link McpAuthorizer}). This
 * filter therefore enforces one coarse rule for the whole surface: the caller must hold
 * {@code artifact.read}. Without it, an authenticated subject with no roles at all — or a
 * platform administrator, who deliberately holds no data-plane permission — can read every
 * artifact of the tenant over MCP.
 *
 * <p>Finer, tool-level requirements (audit, generation) are enforced inside the tools themselves.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class McpAuthorizationFilter extends OncePerRequestFilter {

    private static final String DENIED_BODY =
            "{\"error\":\"forbidden\",\"error_description\":\"missing_artifact_read\"}";

    private final DccPermissions permissions;
    /** The MCP endpoint path, taken from configuration so a changed endpoint cannot skip the gate. */
    private final String endpoint;

    public McpAuthorizationFilter(DccPermissions permissions,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.mcp.server.mcp-endpoint:/mcp}")
            String endpoint) {
        this.permissions = permissions;
        this.endpoint = endpoint == null || endpoint.isBlank() ? "/mcp" : endpoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // only the MCP endpoint itself (with a path boundary); health checks and prompt resources
        // stay open
        String uri = request.getRequestURI();
        return !(uri.equals(endpoint) || uri.startsWith(endpoint + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // fail closed: reaching the MCP endpoint without an authenticated principal is never allowed,
            // regardless of how the security filter chain happens to be ordered
            deny(response);
            return;
        }
        String required = permissions.getArtifactRead();
        boolean granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
        if (!granted) {
            deny(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(DENIED_BODY);
    }
}
