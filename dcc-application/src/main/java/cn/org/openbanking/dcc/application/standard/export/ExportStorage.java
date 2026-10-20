package cn.org.openbanking.dcc.application.standard.export;

import java.nio.file.Path;

/**
 * Object storage for generated export files. Kept as a small interface so tests can
 * capture the generated workbook without an S3/MinIO server.
 */
public interface ExportStorage {

    /** Uploads a local file under {@code key}, replacing any previous object. */
    void put(String key, Path file);

    /** @return a short-lived, pre-signed download URL for the object. */
    String presign(String key, String fileName);

    /** Deletes the object; a missing object is a no-op. */
    void delete(String key);
}
