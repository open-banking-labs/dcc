package cn.org.openbanking.dcc.core.standard.version;

import java.util.List;

/**
 * Outcome of applying {@link VersionBumpPolicy}: the computed change class and
 * the human-readable reasons that drove it, persisted alongside the version for
 * audit and diff display.
 */
public record VersionBump(VersionChangeType type, List<String> reasons) {

    public VersionBump {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
