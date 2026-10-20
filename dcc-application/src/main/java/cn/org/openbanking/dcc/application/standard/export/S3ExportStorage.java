package cn.org.openbanking.dcc.application.standard.export;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible object storage (MinIO in development, any S3-compatible OSS in
 * production). The bucket is private; downloads go through a short-lived pre-signed
 * URL so the application never streams the file itself.
 */
@Component
public class S3ExportStorage implements ExportStorage {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public S3ExportStorage(ExportProperties properties) {
        this.properties = properties;
        ExportProperties.S3 s3 = properties.getS3();
        Region region = Region.of(s3.getRegion());
        AwsCredentialsProvider credentials = credentials(s3);
        S3Configuration service = S3Configuration.builder()
                .pathStyleAccessEnabled(s3.isPathStyle())
                .build();

        var clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(service);
        var presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(service);
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            URI endpoint = URI.create(s3.getEndpoint());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public void put(String key, Path file) {
        ensureBucket();
        client.putObject(PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .contentType(CONTENT_TYPE)
                .build(), file);
    }

    @Override
    public String presign(String key, String fileName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + safeFileName(fileName) + "\"")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.getPresignTtl())
                .getObjectRequest(request)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(builder -> builder
                .bucket(properties.getS3().getBucket())
                .key(key));
    }

    /**
     * Creates the bucket on first use. The app starts without a storage server:
     * uploads simply fail, and the job records that failure.
     */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            String bucket = properties.getS3().getBucket();
            try {
                client.headBucket(builder -> builder.bucket(bucket));
            } catch (NoSuchBucketException ex) {
                client.createBucket(builder -> builder.bucket(bucket));
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    client.createBucket(builder -> builder.bucket(bucket));
                } else {
                    throw ex;
                }
            }
            bucketReady.set(true);
        }
    }

    private static AwsCredentialsProvider credentials(ExportProperties.S3 s3) {
        if (s3.getAccessKey() != null && !s3.getAccessKey().isBlank()
                && s3.getSecretKey() != null && !s3.getSecretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    private static String safeFileName(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "standards-export.xlsx" : fileName;
        return value.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
