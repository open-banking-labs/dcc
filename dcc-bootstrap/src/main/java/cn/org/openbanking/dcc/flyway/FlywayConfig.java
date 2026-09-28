package cn.org.openbanking.dcc.flyway;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    /**
     * Custom migration strategy (adds extra logic on top of auto-configuration)
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (flyway) -> {
            // You can perform pre-validation here
            System.out.println("Executing custom Flyway migration strategy...");
            flyway.migrate();
        };
    }

    /**
     * If you need full manual control over Flyway (without auto-configuration)
     */
    // @Bean
    // public Flyway flyway(DataSource dataSource) {
    //     return Flyway.configure()
    //         .dataSource(dataSource)
    //         .locations("classpath:db/migration/ddl", "classpath:db/migration/dml")
    //         .baselineOnMigrate(true)
    //         .baselineVersion("20260101000000")
    //         .load();
    // }

}
