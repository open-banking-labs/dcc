package cn.org.openbanking.dcc.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.org.openbanking.dcc.warmup.spi.WarmupTask;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class WarmupApplicationRunnerTest {

    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

    private static WarmupProperties properties(boolean enabled, boolean async, boolean failFast, String... steps) {
        WarmupProperties properties = new WarmupProperties();
        properties.setEnabled(enabled);
        properties.setAsync(async);
        properties.setFailFast(failFast);
        properties.setSteps(List.of(steps));
        return properties;
    }

    private static WarmupTask recording(String name, List<String> executed) {
        return new WarmupTask() {
            @Override
            public void warmUp() {
                executed.add(name);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static WarmupTask failing(String name, List<String> executed) {
        return new WarmupTask() {
            @Override
            public void warmUp() {
                executed.add(name);
                throw new IllegalStateException("boom");
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /** Captures the shutdown request instead of exiting the JVM. */
    private static class RecordingRunner extends WarmupApplicationRunner {

        final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

        RecordingRunner(WarmupProperties properties, List<WarmupTask> tasks, ConfigurableApplicationContext context) {
            super(properties, tasks, context);
        }

        @Override
        protected void shutdownAndExit() {
            shutdownRequested.set(true);
        }
    }

    @Test
    void skipsWhenDisabled() {
        List<String> executed = new CopyOnWriteArrayList<>();
        RecordingRunner runner = new RecordingRunner(
                properties(false, false, true, "a"), List.of(recording("a", executed)), context);

        runner.run(null);

        assertThat(executed).isEmpty();
    }

    @Test
    void runsStepsInTheOrderGivenByConfiguration() {
        List<String> executed = new CopyOnWriteArrayList<>();
        RecordingRunner runner = new RecordingRunner(
                properties(true, false, true, "b", "a"),
                List.of(recording("a", executed), recording("b", executed)),
                context);

        runner.run(null);

        assertThat(executed).containsExactly("b", "a");
    }

    @Test
    void failFastRequestsShutdownAndStops() {
        List<String> executed = new CopyOnWriteArrayList<>();
        RecordingRunner runner = new RecordingRunner(
                properties(true, false, true, "boom", "after"),
                List.of(failing("boom", executed), recording("after", executed)),
                context);

        runner.run(null);

        assertThat(runner.shutdownRequested).isTrue();
        assertThat(executed).containsExactly("boom"); // "after" is never reached
    }

    @Test
    void ignoredFailureContinues() {
        List<String> executed = new CopyOnWriteArrayList<>();
        RecordingRunner runner = new RecordingRunner(
                properties(true, false, false, "boom", "after"),
                List.of(failing("boom", executed), recording("after", executed)),
                context);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        assertThat(runner.shutdownRequested).isFalse();
        assertThat(executed).containsExactly("boom", "after");
    }

    @Test
    void configuredStepWithoutMatchingTaskIsAFailure() {
        RecordingRunner runner = new RecordingRunner(properties(true, false, true, "ghost"), List.of(), context);

        runner.run(null);

        assertThat(runner.shutdownRequested).isTrue();
    }

    @Test
    void asyncRunsOffTheStartupThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        WarmupTask slow = new WarmupTask() {
            @Override
            public void warmUp() {
                done.countDown();
            }

            @Override
            public String name() {
                return "slow";
            }
        };
        RecordingRunner runner = new RecordingRunner(properties(true, true, true, "slow"), List.of(slow), context);

        runner.run(null); // must return immediately, without blocking the startup thread

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runner.shutdownRequested).isFalse();
    }
}
