package cn.org.openbanking.dcc.core.common.audit;

/**
 * The "who / why" of the current change, as seen by the audit trail.
 *
 * <p>Declared here (in {@code dcc-core}) as a narrow, transport-agnostic port so the
 * audit machinery never depends on the exposure layer. The use-case layer supplies the
 * implementation, backed by its own provenance context (see {@code ChangeContext} in
 * {@code dcc-application}); when no provider is available the audit trail falls back to
 * a system actor.
 */
public interface AuditIdentityProvider {

    /** @return the authenticated subject that made the change, or {@code null}. */
    String actor();

    /** @return the human-supplied reason for the change, or {@code null}. */
    String reason();
}
