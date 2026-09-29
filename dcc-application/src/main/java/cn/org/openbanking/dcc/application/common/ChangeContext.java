package cn.org.openbanking.dcc.application.common;

import java.util.function.Supplier;

import cn.org.openbanking.dcc.application.tenant.TenantProperties;

import org.springframework.stereotype.Component;

/**
 * The "who / why" of the current change, replayed onto each version record (the
 * history/lineage axis). Populated by the exposure layer from the authenticated
 * subject and an optional {@code X-DCC-Change-Reason} header; defaults to the
 * configured system operator ({@code dcc.tenant.system-operator}, default
 * {@code system}) when unset.
 */
@Component
public class ChangeContext {

    private static final ThreadLocal<Change> CURRENT = new ThreadLocal<>();

    private final String systemOperator;

    public ChangeContext(TenantProperties tenantProperties) {
        this.systemOperator = tenantProperties.getSystemOperator();
    }

    public record Change(String author, String message) {
    }

    public void set(String author, String message) {
        CURRENT.set(new Change(author == null || author.isBlank() ? systemOperator : author, message));
    }

    public void clear() {
        CURRENT.remove();
    }

    /** @return the change author, or the system operator when none is bound. */
    public String author() {
        Change change = CURRENT.get();
        return change == null ? systemOperator : change.author();
    }

    /** @return the human-supplied reason, or {@code null} when none is bound. */
    public String message() {
        Change change = CURRENT.get();
        return change == null ? null : change.message();
    }

    /** @return the bound reason, or the fallback supplier's value when none is bound. */
    public String messageOr(Supplier<String> fallback) {
        String message = message();
        return (message == null || message.isBlank()) ? fallback.get() : message;
    }
}
