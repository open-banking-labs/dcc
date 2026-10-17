package cn.org.openbanking.dcc.application.common;

import cn.org.openbanking.dcc.core.common.audit.AuditIdentityProvider;

import org.springframework.stereotype.Component;

/**
 * Bridges the use-case layer's provenance context ({@link ChangeContext}) to the audit
 * trail's transport-agnostic {@link AuditIdentityProvider} port, so {@code dcc-core} can
 * stay free of any exposure-layer concern.
 */
@Component
public class ChangeContextAuditIdentityProvider implements AuditIdentityProvider {

    private final ChangeContext changeContext;

    public ChangeContextAuditIdentityProvider(ChangeContext changeContext) {
        this.changeContext = changeContext;
    }

    @Override
    public String actor() {
        return changeContext.author();
    }

    @Override
    public String reason() {
        return changeContext.message();
    }
}
