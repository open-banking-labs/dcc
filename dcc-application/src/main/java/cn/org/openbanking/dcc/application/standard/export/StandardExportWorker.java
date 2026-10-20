package cn.org.openbanking.dcc.application.standard.export;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background consumer of the export queue.
 *
 * <p>Enabled only where {@code dcc.export.worker-enabled=true} (dcc-web); the MCP
 * application shares the application layer but must not consume export jobs. The
 * fixed delay prevents overlapping passes in one instance, and the repository's
 * {@code skip locked} claim keeps several instances from processing the same job.
 */
@Component
@ConditionalOnProperty(prefix = "dcc.export", name = "worker-enabled", havingValue = "true")
public class StandardExportWorker {

    private final StandardExportService service;

    public StandardExportWorker(StandardExportService service) {
        this.service = service;
    }

    /** Recovers jobs left RUNNING by a crash before this instance starts serving. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleJobs() {
        service.failStaleJobs();
    }

    @Scheduled(fixedDelayString = "${dcc.export.worker-delay-ms:2000}")
    public void processPending() {
        service.processNextJob();
    }

    @Scheduled(cron = "${dcc.export.cleanup-cron:0 15 * * * *}")
    public void cleanup() {
        service.cleanupExpired();
    }
}
