package cn.org.openbanking.dcc.core.standard.version;

/**
 * Class of a version change, following the project's semantic-versioning rule:
 * <ul>
 *   <li>{@link #MAJOR} - breaking change;</li>
 *   <li>{@link #MINOR} - backward-compatible addition;</li>
 *   <li>{@link #PATCH} - non-structural change.</li>
 * </ul>
 */
public enum VersionChangeType {

    MAJOR(3),
    MINOR(2),
    PATCH(1);

    private final int severity;

    VersionChangeType(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** Returns whichever of the two is the more severe change. */
    public static VersionChangeType highest(VersionChangeType a, VersionChangeType b) {
        return a.severity >= b.severity ? a : b;
    }
}
