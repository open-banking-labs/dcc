package cn.org.openbanking.dcc.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.org.openbanking.dcc.AbstractIntegrationTest;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies row-level permission: privileged operations (here, deleting an artifact)
 * require the DCC_ADMIN authority. Requests carry a real HS256 bearer token so the
 * shared security filter chain runs, exactly as in production. The tenant-scoped row
 * filtering itself is covered by the per-artifact integration tests.
 */
@AutoConfigureMockMvc
class RowLevelPermissionTest extends AbstractIntegrationTest {

    private static final String SECRET = "dev-only-change-me-please-use-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    private String token(String... roles) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("tester")
                .claim("tenant", "t-test")
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void deleteWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/tables/999")).andExpect(status().isForbidden());
    }

    @Test
    void deleteWithoutAdminAuthorityIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/tables/999").header("Authorization", "Bearer " + token("DCC_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteWithAdminAuthorityPassesAuthorization() throws Exception {
        // authorised, so the guard passed and we reach the use case: 404 (no such table), not 403
        mockMvc.perform(delete("/api/tables/999").header("Authorization", "Bearer " + token("DCC_ADMIN")))
                .andExpect(status().isNotFound());
    }
}
