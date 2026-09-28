package cn.org.openbanking.dcc.warmup.task;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisWarmupTaskTest {

    @Test
    void pingsAndClosesTheConnection() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        new RedisWarmupTask(connectionFactory).warmUp();

        verify(connection).ping();
        verify(connection).close();
    }
}
