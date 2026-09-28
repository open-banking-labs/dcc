package cn.org.openbanking.dcc.warmup.task;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import cn.org.openbanking.dcc.warmup.spi.WarmupTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Warms the connection pool by pre-creating its {@code minimumIdle} connections,
 * so the first real request does not pay for establishing them.
 *
 * <p>Registered as {@code warmup-hikari}.
 */
@Component
public class HikariWarmupTask implements WarmupTask {

    private static final Logger log = LoggerFactory.getLogger(HikariWarmupTask.class);

    private final DataSource dataSource;

    public HikariWarmupTask(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "warmup-hikari";
    }

    @Override
    public void warmUp() throws Exception {
        int target = 1;
        if (dataSource instanceof HikariDataSource hikari) {
            target = Math.max(hikari.getMinimumIdle(), 1);
        }

        // Hold all the connections at once so the pool is forced to create them.
        List<Connection> held = new ArrayList<>(target);
        try {
            for (int i = 0; i < target; i++) {
                held.add(dataSource.getConnection());
            }
            for (Connection connection : held) {
                if (!connection.isValid(2)) {
                    throw new IllegalStateException("Warmed connection is not valid");
                }
            }
            log.info("Connection pool warmed with {} connection(s)", held.size());
        } finally {
            for (Connection connection : held) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // returning to the pool; nothing useful to do on close failure
                }
            }
        }
    }
}
