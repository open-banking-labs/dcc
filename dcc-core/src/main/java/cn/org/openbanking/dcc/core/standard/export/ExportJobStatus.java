package cn.org.openbanking.dcc.core.standard.export;

/**
 * Lifecycle of an asynchronous standards export.
 *
 * <p>{@code PENDING} jobs are claimed by a worker, which moves them to {@code RUNNING}
 * and finally {@code READY} (object uploaded) or {@code FAILED}. A {@code READY} job
 * becomes {@code EXPIRED} once its object passes the retention window.
 */
public enum ExportJobStatus {
    PENDING,
    RUNNING,
    READY,
    FAILED,
    EXPIRED
}
