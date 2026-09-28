package cn.org.openbanking.dcc.warmup.task;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

class HikariWarmupTaskTest {

    @Test
    void opensValidatesAndReleasesAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        new HikariWarmupTask(dataSource).warmUp();

        verify(dataSource, times(1)).getConnection();
        verify(connection).isValid(2);
        verify(connection).close();
    }
}
