package cn.org.openbanking.dcc.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security for the thin exposure layers.
 *
 * <p>Stateless: every request is authenticated from its bearer JWT (verified
 * here, in one place shared by dcc-web and dcc-mcp), not a session. Paths in
 * {@code dcc.security.permit-paths} stay open (health, API docs); everything else
 * requires a valid token. Method-level authorization ({@code @PreAuthorize}) is
 * enabled so finer, domain-aware rules live next to the code they guard; the
 * required authorities are named in config and referenced as
 * {@code @dccAuthorities.*} so no authority literal is hard-coded. CORS is applied
 * only when {@code dcc.security.cors.allowed-origins} is set.
 *
 * <p>Discovered by the applications' component scan (package
 * {@code cn.org.openbanking.dcc.security}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class DccSecurityConfiguration {

    /**
     * Verifies tokens with the identity provider's JWKS when configured,
     * otherwise with an HS256 shared secret (development).
     */
    @Bean
    public JwtDecoder dccJwtDecoder(DccSecurityProperties properties) {
        if (StringUtils.hasText(properties.getJwkSetUri())) {
            return NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        }
        SecretKey key = new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public BearerJwtAuthenticationFilter bearerJwtAuthenticationFilter(
            JwtDecoder decoder, DccSecurityProperties properties, RolePermissionResolver rolePermissionResolver) {
        return new BearerJwtAuthenticationFilter(decoder, properties, rolePermissionResolver);
    }

    @Bean
    public SecurityFilterChain dccSecurityFilterChain(HttpSecurity http,
            BearerJwtAuthenticationFilter bearerJwtFilter,
            DccSecurityProperties properties) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!properties.getCors().getAllowedOrigins().isEmpty()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource(properties.getCors())));
        }
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.getPermitPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearerJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsConfigurationSource(DccSecurityProperties.Cors cors) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.getAllowedOrigins());
        configuration.setAllowedMethods(cors.getAllowedMethods());
        configuration.setAllowedHeaders(cors.getAllowedHeaders());
        configuration.setAllowCredentials(cors.isAllowCredentials());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
