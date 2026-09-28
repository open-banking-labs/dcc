package cn.org.openbanking.dcc.warmup.task;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import cn.org.openbanking.dcc.warmup.spi.WarmableCache;

import org.junit.jupiter.api.Test;

class JvmCacheWarmupTaskTest {

    @Test
    void warmsEveryRegisteredCache() {
        WarmableCache first = mock(WarmableCache.class);
        WarmableCache second = mock(WarmableCache.class);
        when(first.name()).thenReturn("first");
        when(second.name()).thenReturn("second");

        new JvmCacheWarmupTask(List.of(first, second)).warmUp();

        verify(first).warm();
        verify(second).warm();
    }

    @Test
    void noCachesIsNotAnError() {
        assertThatCode(() -> new JvmCacheWarmupTask(List.of()).warmUp()).doesNotThrowAnyException();
    }
}
