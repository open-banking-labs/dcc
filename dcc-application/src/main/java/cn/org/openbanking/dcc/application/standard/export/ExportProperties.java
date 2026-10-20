package cn.org.openbanking.dcc.application.standard.export;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the asynchronous standards export, bound from
 * {@code dcc.export.*}. Defaults reproduce a working local setup (MinIO on
 * {@code localhost:9000}); every value can be overridden per environment.
 *
 * <pre>
 * dcc:
 *   export:
 *     worker-enabled: true            # only dcc-web turns this on
 *     worker-delay-ms: 2000
 *     presign-ttl: PT10M
 *     retention: PT24H
 *     max-rows: 50000
 *     max-selected: 100
 *     import-max-rows: 2000
 *     s3:
 *       endpoint: http://localhost:9000
 *       region: us-east-1
 *       access-key: minioadmin
 *       secret-key: <MINIO_ROOT_PASSWORD>
 *       bucket: dcc-exports
 *       path-style: true
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.export")
public class ExportProperties {

    /** Enables the scheduled worker. dcc-web sets this to true; dcc-mcp leaves it off. */
    private boolean workerEnabled = false;

    /** Delay between polling passes of the pending-export queue. */
    private long workerDelayMs = 2000;

    /** Cron for deleting objects past their retention window. */
    private String cleanupCron = "0 15 * * * *";

    /** A RUNNING job older than this is considered abandoned and failed on startup. */
    private int staleAfterMinutes = 30;

    /** How long a generated download link stays valid. */
    private Duration presignTtl = Duration.ofMinutes(10);

    /** How long the generated object is kept in the bucket. */
    private Duration retention = Duration.ofHours(24);

    /** Hard cap on exported data rows; larger results fail with a clear message. */
    private int maxRows = 50000;

    /** Hard cap on explicitly selected ids in one export/analysis request. */
    private int maxSelected = 100;

    /** Hard cap on rows accepted by one Excel import. */
    private int importMaxRows = 2000;

    /** Rows fetched per page while streaming an export. */
    private int pageChunkSize = 500;

    private S3 s3 = new S3();

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getWorkerDelayMs() {
        return workerDelayMs;
    }

    public void setWorkerDelayMs(long workerDelayMs) {
        this.workerDelayMs = workerDelayMs;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public int getStaleAfterMinutes() {
        return staleAfterMinutes;
    }

    public void setStaleAfterMinutes(int staleAfterMinutes) {
        this.staleAfterMinutes = staleAfterMinutes;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxSelected() {
        return maxSelected;
    }

    public void setMaxSelected(int maxSelected) {
        this.maxSelected = maxSelected;
    }

    public int getImportMaxRows() {
        return importMaxRows;
    }

    public void setImportMaxRows(int importMaxRows) {
        this.importMaxRows = importMaxRows;
    }

    public int getPageChunkSize() {
        return pageChunkSize;
    }

    public void setPageChunkSize(int pageChunkSize) {
        this.pageChunkSize = pageChunkSize;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    /** S3-compatible endpoint settings; a blank endpoint means the AWS default. */
    public static class S3 {

        private String endpoint = "";

        private String region = "us-east-1";

        private String accessKey = "";

        private String secretKey = "";

        private String bucket = "dcc-exports";

        private boolean pathStyle = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }
    }
}
