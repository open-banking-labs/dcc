package cn.org.openbanking.dcc.application.standard.export;

import java.time.Instant;
import java.util.Optional;

import cn.org.openbanking.dcc.core.standard.export.ExportJob;
import cn.org.openbanking.dcc.core.standard.export.repository.ExportJobRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short transactional step of claiming a job. It lives in its own bean so the
 * claim commits before {@link StandardExportService} starts the long generation —
 * calling a {@code @Transactional} method on the same bean would bypass the proxy.
 */
@Component
public class ExportJobQueue {

    private final ExportJobRepository jobs;

    public ExportJobQueue(ExportJobRepository jobs) {
        this.jobs = jobs;
    }

    /** Claims the oldest pending job, or returns empty when the queue is idle. */
    @Transactional
    public Optional<Long> claimNext() {
        return jobs.lockNextPending().map(job -> {
            job.markRunning(Instant.now());
            jobs.save(job);
            return job.getId();
        });
    }
}
