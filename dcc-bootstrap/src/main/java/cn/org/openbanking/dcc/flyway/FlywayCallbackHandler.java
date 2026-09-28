package cn.org.openbanking.dcc.flyway;

import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlywayCallbackHandler extends BaseCallback {

    private static final Logger log = LoggerFactory.getLogger(FlywayCallbackHandler.class);

    @Override
    public boolean supports(Event event, Context context) {
        // Listen to all events
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        switch (event) {
            case BEFORE_MIGRATE:
                log.info("=== Starting Flyway migration ===");
                // Run pre-migration SQL (for example, set lock timeout).
                // spring.flyway.init-sqls already sets this on every connection;
                // this is a belt-and-braces repeat, so a failure here is logged
                // rather than allowed to abort an otherwise valid migration.
                setLockTimeout(context);
                break;
            case BEFORE_EACH_MIGRATE:
                log.info("Executing script: {}", context.getMigrationInfo().getScript());
                break;
            case AFTER_EACH_MIGRATE:
                log.info("Script executed successfully: {}", context.getMigrationInfo().getScript());
                break;
            case AFTER_MIGRATE:
                log.info("=== Flyway migration completed successfully ===");
                break;
            case AFTER_MIGRATE_ERROR:
                log.error("=== Flyway migration failed; please check the script ===");
                break;
            default:
                break;
        }
    }

    /**
     * Caps how long a migration will wait on a lock. Without it a migration
     * blocked behind an open transaction can hang indefinitely.
     */
    private void setLockTimeout(Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("SET SESSION lock_timeout = '2s'");
        } catch (SQLException e) {
            log.warn("Could not set lock_timeout; migrations will run without it", e);
        }
    }
}
