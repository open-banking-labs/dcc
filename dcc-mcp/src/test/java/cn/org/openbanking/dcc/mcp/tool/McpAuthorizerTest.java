package cn.org.openbanking.dcc.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import cn.org.openbanking.dcc.security.DccPermissions;
import cn.org.openbanking.dcc.security.DccSecurityProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Proves the MCP surface is authorized without relying on AOP interception: the filter guards
 * the whole surface, the authorizer guards individual tools (ADR 0005).
 */
class McpAuthorizerTest {

    private final DccSecurityProperties properties = new DccSecurityProperties();
    private final DccPermissions permissions = new DccPermissions(properties);
    private final McpAuthorizer authorizer = new McpAuthorizer(permissions);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice",
                "n/a", List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void auditAccessRequiresTheAuditBusinessPermission() {
        authenticate(permissions.getArtifactRead());

        assertThatThrownBy(authorizer::requireAuditBusiness).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void auditAccessIsGrantedToTheTenantAdministrator() {
        authenticate(permissions.getArtifactRead(), permissions.getAuditBusiness());

        authorizer.requireAuditBusiness();
    }

    @Test
    void anonymousCallerIsDenied() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(authorizer::requireGenerationRead).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void mcpSurfaceBaselineRequiresArtifactRead() throws Exception {
        McpAuthorizationFilter filter = new McpAuthorizationFilter(permissions, "/mcp");

        // a platform administrator holds no data-plane permission and must not read artifacts
        authenticate(permissions.getTenantAdmit());
        var denied = new org.springframework.mock.web.MockHttpServletResponse();
        var deniedRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/mcp");
        filter.doFilter(deniedRequest, denied, (req, res) -> {
            throw new AssertionError("must not be chained");
        });
        assertThat(denied.getStatus()).isEqualTo(403);

        authenticate(permissions.getArtifactRead());
        var allowed = new org.springframework.mock.web.MockHttpServletResponse();
        var allowedRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/mcp");
        boolean[] chained = {false};
        filter.doFilter(allowedRequest, allowed, (req, res) -> chained[0] = true);
        assertThat(chained[0]).isTrue();
    }

    @Test
    void nonMcpPathsAreNotFiltered() throws Exception {
        McpAuthorizationFilter filter = new McpAuthorizationFilter(permissions, "/mcp");
        authenticate(permissions.getTenantAdmit());

        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/actuator/health");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        boolean[] chained = {false};

        filter.doFilter(request, response, (req, res) -> chained[0] = true);

        assertThat(chained[0]).isTrue();
    }
}
