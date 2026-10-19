package cn.org.openbanking.dcc.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the caller from the {@code Authorization: Bearer <jwt>} header:
 * verifies the token, builds an {@link InternalIdentity} (subject, tenant, roles)
 * as the principal, and scopes the request to its tenant via {@link TenantContext}.
 *
 * <p>The exposure layers are only reachable through the gateway, but the token
 * itself is authoritative here - the gateway does not need to validate it, so
 * "who the caller is" is decided in exactly one place (this shared module).
 *
 * <p>In directory mode ({@code dcc.security.directory-mode}), the verified token is
 * only the identity: the {@link DirectoryAccessPolicy} additionally gates the
 * subject/tenant pair (a 403 {@code not_a_directory_member} when no membership)
 * and unions tenant-assigned roles with the claim roles before authority mapping.
 */
public class BearerJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SCOPE_HEADER = "X-DCC-Scope";
    private static final Set<String> EXPLICIT_SCOPE_PATHS = Set.of(
            "/api/standards", "/api/standards/export", "/api/standards/import",
            "/api/tables", "/api/interfaces", "/api/templates",
            "/api/generation/openapi", "/api/generation/jar", "/api/versioning/drift");

    private final JwtDecoder jwtDecoder;
    private final DccSecurityProperties properties;
    private final RolePermissionResolver rolePermissionResolver;
    /** Only populated in directory mode; may be {@code null} otherwise. */
    private final @Nullable DirectoryAccessPolicy directoryPolicy;
    /** Resolves break-glass permission names; {@code null} means "use the configured literal". */
    private final @Nullable DccPermissions dccPermissions;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BearerJwtAuthenticationFilter.class);

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver) {
        this(jwtDecoder, properties, rolePermissionResolver, null, null);
    }

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver, @Nullable DirectoryAccessPolicy directoryPolicy) {
        this(jwtDecoder, properties, rolePermissionResolver, directoryPolicy, null);
    }

    public BearerJwtAuthenticationFilter(JwtDecoder jwtDecoder, DccSecurityProperties properties,
            RolePermissionResolver rolePermissionResolver, @Nullable DirectoryAccessPolicy directoryPolicy,
            @Nullable DccPermissions dccPermissions) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.rolePermissionResolver = rolePermissionResolver;
        this.directoryPolicy = directoryPolicy;
        this.dccPermissions = dccPermissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
                List<String> roles = jwt.getClaimAsStringList(properties.getRolesClaim());
                Set<String> roleSet = roles == null ? Set.of() : new HashSet<>(roles);
                String subject = jwt.getSubject();
                String tenant = jwt.getClaimAsString(properties.getTenantClaim());
                if (properties.isDirectoryMode()) {
                    // claim-only identity: the directory decides membership and adds tenant roles
                    if (!StringUtils.hasText(subject) || !StringUtils.hasText(tenant)) {
                        reject(response,
                                "directory_identity_required",
                                "directory mode requires the subject and tenant claims");
                        return;
                    }
                    if (!directoryPolicy.isMember(subject, tenant)) {
                        reject(response, "not_a_directory_member",
                                "subject '" + subject + "' has no membership in tenant '" + tenant + "'");
                        return;
                    }
                    roleSet = new HashSet<>(roleSet);
                    roleSet.addAll(directoryPolicy.assignedRoles(subject, tenant));
                    if (supportsExplicitScope(request)) {
                        for (DirectoryScope scope : DirectoryScope.parseAll(request.getHeader(SCOPE_HEADER))) {
                            Long targetId = requestParameterAsLong(request, scope.requestParameter());
                            if (targetId != null) {
                                roleSet.addAll(directoryPolicy.assignedRoles(subject, tenant,
                                        scope.type(), scope.code(), targetId));
                            }
                        }
                    }
                }
                InternalIdentity identity = new InternalIdentity(subject, tenant, roleSet);
                Set<String> granted = new LinkedHashSet<>(rolePermissionResolver.authoritiesFor(roleSet));
                granted.addAll(breakGlassAuthorities(request, subject));
                var authorities = granted.stream()
                        .map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(identity, header, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                TenantContext.set(identity.tenant());
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid_token\"}");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Emergency access (ADR 0005 §D10): a small, configured set of subjects may act one request at a
     * time with a fixed set of extra permissions — but only if they state a reason.
     *
     * <p>Called after the directory membership gate on purpose: break-glass widens what a known
     * member may do; it never lets an outsider into a tenant.
     */
    private Set<String> breakGlassAuthorities(HttpServletRequest request, @Nullable String subject) {
        DccSecurityProperties.BreakGlass breakGlass = properties.getBreakGlass();
        if (breakGlass == null || !StringUtils.hasText(subject)
                || breakGlass.getSubjects() == null || !breakGlass.getSubjects().contains(subject)
                || breakGlass.getPermissions() == null || breakGlass.getPermissions().isEmpty()) {
            return Set.of();
        }
        String reason = request.getHeader(breakGlass.getReasonHeader());
        if (!StringUtils.hasText(reason)) {
            // no stated reason, no emergency: the caller keeps exactly its normal authorities
            return Set.of();
        }
        log.warn("BREAK-GLASS: subject '{}' acts with {} on {} {} — reason: {}", subject,
                breakGlass.getPermissions(), request.getMethod(), request.getRequestURI(), reason);
        Set<String> granted = new LinkedHashSet<>();
        for (String permission : breakGlass.getPermissions()) {
            granted.add(dccPermissions == null ? permission : dccPermissions.resolve(permission));
        }
        return granted;
    }

    private static Long requestParameterAsLong(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean supportsExplicitScope(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return EXPLICIT_SCOPE_PATHS.contains(path);
    }

    private record DirectoryScope(String type, String code, String requestParameter) {

        private static List<DirectoryScope> parseAll(String header) {
            if (!StringUtils.hasText(header)) {
                return List.of();
            }
            return java.util.Arrays.stream(header.split(";"))
                    .map(String::trim)
                    .map(DirectoryScope::parseOne)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
        }

        private static DirectoryScope parseOne(String value) {
            int separator = value.indexOf('=');
            if (separator <= 0 || separator == value.length() - 1
                    || value.indexOf('=', separator + 1) >= 0) {
                return null;
            }
            String kind = value.substring(0, separator).trim();
            String code = value.substring(separator + 1).trim();
            if (!code.matches("[A-Za-z0-9._-]{1,64}")) {
                return null;
            }
            return switch (kind) {
                case "environment" -> new DirectoryScope("ENVIRONMENT", code, "environmentId");
                case "application" -> new DirectoryScope("APPLICATION", code, "applicationId");
                default -> null;
            };
        }
    }

    /** Directory rejections happen before authentication exists, so answer directly (403). */
    private static void reject(HttpServletResponse response, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
