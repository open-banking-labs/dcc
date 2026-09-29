package cn.org.openbanking.dcc.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Proves that {@code @PreAuthorize("hasAuthority(@dccAuthorities.admin)")} resolves the
 * authority from configuration: the default {@code DCC_ADMIN} works, and overriding
 * {@code dcc.security.authorities.admin} changes what the guard requires.
 */
class DccAuthoritiesMethodSecurityTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityTestConfig.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void configuredAuthorityGrantsAndDenies() {
        runner.run(context -> {
            Protected protectedBean = context.getBean(Protected.class);

            authenticate("DCC_ADMIN");
            assertThatCode(protectedBean::adminOnly).doesNotThrowAnyException();

            authenticate("DCC_USER");
            assertThatThrownBy(protectedBean::adminOnly).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void overriddenAuthorityNameIsHonoured() {
        runner.withPropertyValues("dcc.security.authorities.admin=SUPER_ADMIN")
                .run(context -> {
                    Protected protectedBean = context.getBean(Protected.class);

                    authenticate("DCC_ADMIN");
                    assertThatThrownBy(protectedBean::adminOnly).isInstanceOf(AccessDeniedException.class);

                    authenticate("SUPER_ADMIN");
                    assertThatCode(protectedBean::adminOnly).doesNotThrowAnyException();
                });
    }

    private static void authenticate(String... authorities) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "user", "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @EnableConfigurationProperties(DccSecurityProperties.class)
    @Import(DccAuthorities.class)
    static class SecurityTestConfig {

        @Bean
        Protected protectedBean() {
            return new Protected();
        }
    }

    static class Protected {

        @PreAuthorize("hasAuthority(@dccAuthorities.admin)")
        public String adminOnly() {
            return "ok";
        }
    }
}
