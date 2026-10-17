package cn.org.openbanking.dcc.mcp;

import java.util.Set;

import cn.org.openbanking.dcc.application.tenant.TenantDirectoryPolicy;
import cn.org.openbanking.dcc.security.DirectoryAccessPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Maps the application-owned directory policy onto the security module's
 * {@code DirectoryAccessPolicy} seam for the MCP exposure layer (same bridge as
 * dcc-web; the two halves only meet in the exposure layers).
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

            @Override
            public Set<String> assignedRoles(String subject, String tenantId, String scopeType,
                    String scopeCode, Long targetId) {
                return policy.assignedRoles(subject, tenantId, scopeType, scopeCode, targetId);
            }
        };
    }
}
