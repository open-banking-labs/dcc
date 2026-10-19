package cn.org.openbanking.dcc.application.common;

import cn.org.openbanking.dcc.core.common.error.ConflictException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The four-eyes principle (ADR 0005 §D4): whoever produced the change under review cannot be the
 * one who approves it.
 *
 * <p>Enforced in the use-case layer rather than at the transport, because both the migration
 * approval and the artifact lifecycle transitions need the same rule and neither the caller's
 * roles nor the URL carry who submitted the change.
 *
 * <p>The comparison is by <em>identity</em>, not by role: a small team may legitimately hold the
 * maintainer and approver roles at once, but a single person still cannot approve their own work.
 * A deployment therefore needs two distinct approvers before production use, otherwise approval
 * deadlocks.
 */
@Component
public class SeparationOfDuties {

    private static final Logger log = LoggerFactory.getLogger(SeparationOfDuties.class);

    private final ChangeContext changeContext;

    public SeparationOfDuties(ChangeContext changeContext) {
        this.changeContext = changeContext;
    }

    /**
     * Rejects the approval when the current change author is the author/submitter under review.
     *
     * @param author whoever produced the change being approved; {@code null} or blank means the
     *               origin is unknown (e.g. a migration written before this rule existed), in which
     *               case the check cannot fire and the approval proceeds
     * @param what   human-readable description used in the error message
     */
    public void requireFourEyes(String author, String what) {
        String current = changeContext.boundAuthor();
        if (current == null) {
            // no authenticated caller is bound (system job / seeder): there is no second human to
            // require. The exposure layers always bind one via ChangeContextFilter, so a real
            // request never reaches this branch.
            if (author != null && !author.isBlank()) {
                log.warn("four-eyes not evaluated for {}: no authenticated caller is bound (submitted by '{}')",
                        what, author);
            }
            return;
        }
        if (author == null || author.isBlank()) {
            // origin unknown (e.g. a migration order submitted before submitted_by existed): document it
            // rather than pass silently — an approver cannot be told apart from the submitter
            log.warn("four-eyes not evaluated for {}: submitter unknown", what);
            return;
        }
        if (author.equals(current)) {
            throw new ConflictException("four-eyes principle: '" + current + "' submitted " + what
                    + " and cannot also approve it; a different approver is required");
        }
    }
}
