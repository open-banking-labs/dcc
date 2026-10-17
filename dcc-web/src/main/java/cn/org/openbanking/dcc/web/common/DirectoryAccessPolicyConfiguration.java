package cn.org.openbanking.dcc.web.common;

import java.util.Set;

import cn.org.openbanking.dcc.application.tenant.TenantDirectoryPolicy;
import cn.org.openbanking.dcc.security.DirectoryAccessPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Maps the application-owned directory policy onto the security module's
 * {@code DirectoryAccessPolicy} seam. The bridge lives in the exposure layer because
 * {@code dcc-application} must not depend on security types and {@code dcc-security}
 * must not depend on persistence — this configuration is the only point where the two
 * halves meet.
 */
@Configuration
public class DirectoryAccessPolicyConfiguration {

    @Bean
    DirectoryAccessPolicy directoryAccessPolicy(TenantDirectoryPolicy policy) {
        return new DirectoryAccessPolicy() {
            @Override
            public boolean isMember(String subject, String tenantId) {
                return policy.isMember(subject, tenantId);
            }

            @Override
            public Set<String> assignedRoles(String subject, String tenantId) {
                return policy.assignedRoles(subject, tenantId);
            }
        };
    }
}
