package cn.org.openbanking.dcc.application.common;

/**
 * The operating surface one resource belongs to: which environment and application it lives in,
 * with the tenant-unique business codes a scoped directory grant is addressed by.
 *
 * <p>Codes are resolved from the database rather than taken from the request, so a caller cannot
 * forge its way into a scoped grant by claiming a surface (ADR 0004 §3, ADR 0005).
 */
public record OperatingSurface(Long environmentId, String environmentCode,
        Long applicationId, String applicationCode) {
}
