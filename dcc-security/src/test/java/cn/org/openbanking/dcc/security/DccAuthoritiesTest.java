package cn.org.openbanking.dcc.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DccAuthoritiesTest {

    @Test
    void defaultsMatchTheHistoricalAuthorityNames() {
        DccAuthorities authorities = new DccAuthorities(new DccSecurityProperties());

        assertThat(authorities.getAdmin()).isEqualTo("DCC_ADMIN");
        assertThat(authorities.getApprover()).isEqualTo("DCC_APPROVER");
        assertThat(authorities.getUser()).isEqualTo("DCC_USER");
    }

    @Test
    void configuredNamesOverrideTheDefaults() {
        DccSecurityProperties properties = new DccSecurityProperties();
        properties.getAuthorities().put("admin", "SUPER_ADMIN");

        DccAuthorities authorities = new DccAuthorities(properties);

        assertThat(authorities.getAdmin()).isEqualTo("SUPER_ADMIN");
        assertThat(authorities.getApprover()).isEqualTo("DCC_APPROVER");
    }
}
