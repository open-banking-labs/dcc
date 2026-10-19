package cn.org.openbanking.dcc.web.common;

import java.util.List;

import cn.org.openbanking.dcc.security.InternalIdentity;
import cn.org.openbanking.dcc.security.TenantContext;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's effective permissions (ADR 0005).
 *
 * <p>The console must not derive its menus from the raw {@code roles} JWT claim: the claim is an
 * identity-provider artefact, while the effective permission points are the union of claim roles,
 * directory assignments and role bundles resolved here on the server. This endpoint is the single
 * source of truth for client-side gating; it is deliberately readable by any authenticated caller
 * and exposes nothing but the caller's own identity, tenant and permission points.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record MeView(String subject, String tenant, List<String> permissions) {
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public MeView me(Authentication authentication) {
        List<String> permissions = authentication == null ? List.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        String subject = null;
        if (authentication != null && authentication.getPrincipal() instanceof InternalIdentity identity) {
            subject = identity.subject();
        } else if (authentication != null) {
            subject = authentication.getName();
        }
        return new MeView(subject, TenantContext.get(), permissions);
    }
}
