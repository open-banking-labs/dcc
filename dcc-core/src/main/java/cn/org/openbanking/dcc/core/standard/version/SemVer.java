package cn.org.openbanking.dcc.core.standard.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.org.openbanking.dcc.core.common.error.ValidationException;

/**
 * Immutable semantic version ({@code major.minor.patch}) used by every versioned
 * artifact (standard, table structure, interface, template).
 *
 * <p>Ordering is by major, then minor, then patch; {@link #bump(VersionChangeType)}
 * produces the next version for a given change class.
 */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    /** Version assigned to an artifact's first revision. */
    public static final SemVer INITIAL = new SemVer(1, 0, 0);

    private static final Pattern PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version parts must be non-negative");
        }
    }

    /** Parses {@code "major.minor.patch"}; rejects anything else. */
    public static SemVer parse(String text) {
        if (text == null) {
            throw new ValidationException("version must not be null");
        }
        Matcher matcher = PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            throw new ValidationException("not a semantic version: " + text);
        }
        return new SemVer(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    /** The next version for the given change class. */
    public SemVer bump(VersionChangeType type) {
        return switch (type) {
            case MAJOR -> new SemVer(major + 1, 0, 0);
            case MINOR -> new SemVer(major, minor + 1, 0);
            case PATCH -> new SemVer(major, minor, patch + 1);
        };
    }

    /** Canonical {@code major.minor.patch} text. */
    public String value() {
        return major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(SemVer other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return value();
    }
}
