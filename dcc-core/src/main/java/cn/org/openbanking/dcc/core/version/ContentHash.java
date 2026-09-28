package cn.org.openbanking.dcc.core.version;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The <b>content-hash axis</b> of the versioning model: a SHA-256 fingerprint of an
 * artifact's canonicalised semantic body.
 *
 * <p>Two models with the same content hash are semantically identical, so the hash
 * answers "are these the same model?" (identity) and, recomputed over a stored
 * snapshot, "was this snapshot tampered with?" (integrity). It is deliberately
 * independent of the semantic version: the hash is the <em>identity</em>, the SemVer
 * is the <em>compatibility promise</em> attached to a hash transition.
 */
public final class ContentHash {

    private static final String PREFIX = "sha256:";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ContentHash() {
    }

    /** Hashes a canonical (deterministic) serialisation of the content. */
    public static String sha256(String canonicalForm) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalForm.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(PREFIX.length() + bytes.length * 2).append(PREFIX);
            for (byte b : bytes) {
                hex.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** @return true when {@code expected} equals the hash of {@code canonicalForm}. */
    public static boolean matches(String expected, String canonicalForm) {
        return expected != null && expected.equals(sha256(canonicalForm));
    }
}
