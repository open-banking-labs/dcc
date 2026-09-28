package cn.org.openbanking.dcc.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Smoke test: boots the MCP application against a throwaway PostgreSQL. Flyway is
 * enabled here (the app disables it in favour of the schema owner) so the
 * {@code ddl-auto: validate} check runs against a real, migrated schema.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/ddl,classpath:db/migration/dml,"
                + "classpath:db/migration/function,classpath:db/migration/index"
})
class DccMcpApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void contextLoads() {
    }

}
