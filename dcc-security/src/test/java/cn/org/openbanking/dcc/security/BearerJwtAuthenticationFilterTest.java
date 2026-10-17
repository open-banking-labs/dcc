package cn.org.openbanking.dcc.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class BearerJwtAuthenticationFilterTest {

    private final String secret = "test-secret-please-use-32-bytes-minimum";
    private final DccSecurityProperties properties = new DccSecurityProperties();
    private final JwtDecoder decoder;
    private final BearerJwtAuthenticationFilter filter;

    BearerJwtAuthenticationFilterTest() {
        properties.setJwtSecret(secret);
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.filter = new BearerJwtAuthenticationFilter(decoder, properties,
                new ConfigRolePermissionResolver(properties));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private String sign(String subject, String tenant, List<String> roles) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("tenant", tenant)
                .claim("roles", roles)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void validTokenEstablishesIdentityAndTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sign("alice", "tenant-a", List.of("ROLE_EDITOR")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chained.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isInstanceOf(InternalIdentity.class);
            assertThat(((InternalIdentity) authentication.getPrincipal()).tenant()).isEqualTo("tenant-a");
            assertThat(TenantContext.get()).isEqualTo("tenant-a");
        });

        assertThat(chained).isTrue();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void roleMappingExpandsAuthorities() throws Exception {
        properties.getRoleAuthorities().put("ROLE_EDITOR", List.of("DCC_APPROVER"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sign("bob", "tenant-b", List.of("ROLE_EDITOR")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .containsExactly("DCC_APPROVER"));
    }

    @Test
    void invalidTokenIsRejectedWithUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not.a.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingTokenIsLeftUnauthenticatedForTheChainToReject() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void scopedRoleRequiresHeaderAndMatchingExplicitTargetParameter() throws Exception {
        properties.setDirectoryMode(true);
        DirectoryAccessPolicy scopedPolicy = new DirectoryAccessPolicy() {
            @Override
            public boolean isMember(String subject, String tenantId) {
                return true;
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId) {
                return Set.of();
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
                    String scopeCode, Long targetId) {
                return "ENVIRONMENT".equals(scopeType) && "DEV".equals(scopeCode)
                        && Long.valueOf(7).equals(targetId) ? Set.of("DCC_ADMIN") : Set.of();
            }
        };
        BearerJwtAuthenticationFilter scopedFilter = new BearerJwtAuthenticationFilter(decoder, properties,
                new ConfigRolePermissionResolver(properties), scopedPolicy);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/standards");
        request.addHeader("Authorization", "Bearer " + sign("alice", "tenant-a", List.of()));
        request.addHeader("X-DCC-Scope", "environment=DEV");
        request.addParameter("environmentId", "7");

        scopedFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertThat(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .contains("DCC_ADMIN"));
    }

    @Test
    void scopedRoleIsNotActivatedWithoutExplicitTargetParameter() throws Exception {
        properties.setDirectoryMode(true);
        DirectoryAccessPolicy scopedPolicy = new DirectoryAccessPolicy() {
            @Override
            public boolean isMember(String subject, String tenantId) {
                return true;
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId) {
                return Set.of();
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
                    String scopeCode, Long targetId) {
                return Set.of("DCC_ADMIN");
            }
        };
        BearerJwtAuthenticationFilter scopedFilter = new BearerJwtAuthenticationFilter(decoder, properties,
                new ConfigRolePermissionResolver(properties), scopedPolicy);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/standards");
        request.addHeader("Authorization", "Bearer " + sign("alice", "tenant-a", List.of()));
        request.addHeader("X-DCC-Scope", "environment=DEV");

        scopedFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertThat(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty());
    }

    @Test
    void opaqueResourceEndpointCannotActivateScopeByInjectingAQueryParameter() throws Exception {
        properties.setDirectoryMode(true);
        DirectoryAccessPolicy scopedPolicy = new DirectoryAccessPolicy() {
            @Override
            public boolean isMember(String subject, String tenantId) {
                return true;
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId) {
                return Set.of();
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
                    String scopeCode, Long targetId) {
                return Set.of("DCC_ADMIN");
            }
        };
        BearerJwtAuthenticationFilter scopedFilter = new BearerJwtAuthenticationFilter(decoder, properties,
                new ConfigRolePermissionResolver(properties), scopedPolicy);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/standards/42");
        request.addHeader("Authorization", "Bearer " + sign("alice", "tenant-a", List.of()));
        request.addHeader("X-DCC-Scope", "environment=DEV");
        request.addParameter("environmentId", "7");

        scopedFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> assertThat(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty());
    }

    // ------------------------------------------------------------------ directory mode

    /** Fake policy: only charlie is a member of tenant-a, granted DCC_ADMIN there. */
    private DirectoryAccessPolicy policy() {
        return new DirectoryAccessPolicy() {
            @Override
            public boolean isMember(String subject, String tenantId) {
                return "charlie".equals(subject) && "tenant-a".equals(tenantId);
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId) {
                return "charlie".equals(subject) && "tenant-a".equals(tenantId)
                        ? Set.of("DCC_ADMIN")
                        : Set.of();
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
                    String scopeCode, Long targetId) {
                return Set.of();
            }
        };
    }

    /** Signs a token with no tenant claim at all (directory-mode identity check). */
    private String signWithoutTenant(String subject, List<String> roles) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("roles", roles)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private BearerJwtAuthenticationFilter directoryModeFilter() {
        properties.setDirectoryMode(true);
        return new BearerJwtAuthenticationFilter(decoder, properties,
                new ConfigRolePermissionResolver(properties), policy());
    }

    @Test
    void directoryModeUnionsAssignedRolesWithClaimRoles() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sign("charlie", "tenant-a", List.of("ROLE_EDITOR")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        directoryModeFilter().doFilter(request, response, (req, res) -> {
            chained.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).toList())
                    .containsExactlyInAnyOrder("DCC_ADMIN", "ROLE_EDITOR");
            assertThat(TenantContext.get()).isEqualTo("tenant-a");
        });

        assertThat(chained).isTrue();
    }

    @Test
    void directoryModeRejectsSubjectsWithNoMembership() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sign("eve", "tenant-a", List.of("DCC_ADMIN", "ROLE_EDITOR")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        directoryModeFilter().doFilter(request, response, (req, res) -> chained.set(true));

        // even a claim-level DCC_ADMIN role cannot buy directory membership
        assertThat(chained).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("not_a_directory_member");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void directoryModeRequiresTheTenantClaim() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + signWithoutTenant("charlie", List.of("DCC_ADMIN")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        directoryModeFilter().doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("directory_identity_required");
    }
}
