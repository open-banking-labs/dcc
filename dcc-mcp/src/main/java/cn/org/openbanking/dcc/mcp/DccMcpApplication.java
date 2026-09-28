package cn.org.openbanking.dcc.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * MCP exposure layer - a standalone Spring Boot application that exposes the
 * protocol-agnostic business logic in {@code dcc-api} (and its JPA entities and
 * repositories in {@code dcc-core}) over MCP (Streamable HTTP).
 *
 * <p>This app owns no business logic and never migrates the schema: Flyway is
 * owned by {@code dcc-starter} / the deployment. It scans the shared
 * {@code cn.org.openbanking.dcc} base package so the beans from {@code dcc-api}
 * and {@code dcc-core} that live outside this module's own package are picked up.
 */
@SpringBootApplication(scanBasePackages = "cn.org.openbanking.dcc")
@ConfigurationPropertiesScan("cn.org.openbanking.dcc")
@EntityScan("cn.org.openbanking.dcc")
@EnableJpaRepositories("cn.org.openbanking.dcc")
public class DccMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DccMcpApplication.class, args);
    }

}
