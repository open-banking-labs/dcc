package cn.org.openbanking.dcc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for integration tests that exercise the real stack: the full Spring
 * context, Flyway migrations and PostgreSQL, all in a throwaway container.
 *
 * <p>The container is a JVM-wide singleton (started once in a static initializer,
 * reaped by Testcontainers' Ryuk on exit) and the datasource is wired via
 * {@link DynamicPropertySource}. This keeps the one cached Spring context valid
 * across every test class instead of pointing at a container that a later class
 * already stopped.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
