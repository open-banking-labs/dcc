package cn.org.openbanking.dcc.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class WarmupApplicationRunnerTest {

    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

    private static WarmupProperties properties(boolean enabled, boolean async, boolean failFast) {
        WarmupProperties properties = new WarmupProperties();
        properties.setEnabled(enabled);
        properties.setAsync(async);
        properties.setFailFast(failFast);
        return properties;
    }

    private static WarmupTask task(String name, AtomicInteger counter, Runnable body) {
        return new WarmupTask() {
            @Override
            public void warmUp() {
                counter.incrementAndGet();
                body.run();
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    void skipsWhenDisabled() {
        AtomicInteger runs = new AtomicInteger();
        WarmupApplicationRunner runner = new WarmupApplicationRunner(
                properties(false, false, true), List.of(task("t", runs, () -> { })), context);

        runner.run(null);

        assertThat(runs).hasValue(0);
    }

    @Test
    void runsEveryTaskWhenEnabled() {
        AtomicInteger runs = new AtomicInteger();
        WarmupApplicationRunner runner = new WarmupApplicationRunner(
                properties(true, false, true),
                List.of(task("a", runs, () -> { }), task("b", runs, () -> { })),
                context);

        runner.run(null);

        assertThat(runs).hasValue(2);
    }

    @Test
    void failFastStopsTheApplicationOnFailure() {
        AtomicInteger runs = new AtomicInteger();
        WarmupApplicationRunner runner = new WarmupApplicationRunner(
                properties(true, false, true),
                List.of(
                        task("boom", runs, () -> { throw new IllegalStateException("boom"); }),
                        task("after", runs, () -> { })),
                context);

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);
        assertThat(runs).hasValue(1); // the failing task aborts the sequence
    }

    @Test
    void ignoredFailureLetsTheRestContinue() {
        AtomicInteger runs = new AtomicInteger();
        WarmupApplicationRunner runner = new WarmupApplicationRunner(
                properties(true, false, false),
                List.of(
                        task("boom", runs, () -> { throw new IllegalStateException("boom"); }),
                        task("after", runs, () -> { })),
                context);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        assertThat(runs).hasValue(2);
    }

    @Test
    void asyncRunsOffTheStartupThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        WarmupApplicationRunner runner = new WarmupApplicationRunner(
                properties(true, true, true), List.of(task("slow", runs, done::countDown)), context);

        runner.run(null); // must return immediately without throwing

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void orderComesFromConfiguration() {
        WarmupProperties properties = properties(true, false, true);
        properties.setOrder(42);

        WarmupApplicationRunner runner = new WarmupApplicationRunner(properties, List.of(), context);

        assertThat(runner.getOrder()).isEqualTo(42);
    }
}
