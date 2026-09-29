package [(${packageName})];

import java.util.regex.Pattern;

/** Generated validation for data standard "[(${code})]". */
public final class [(${className})] {

    public static final String STANDARD = [(${standard})];
    public static final boolean REQUIRED = [(${required})];
    public static final Integer LENGTH = [(${length})];
    public static final String REGEX = [(${regex})];
    private static final Pattern PATTERN = Pattern.compile(REGEX == null ? ".*" : REGEX);

    private [(${className})]() {
    }

    /** @return null when valid, otherwise an error message. */
    public static String validate(String value) {
        if (value == null || value.isEmpty()) {
            return REQUIRED ? [(${requiredMessage})] : null;
        }
        if (REGEX != null && !PATTERN.matcher(value).matches()) {
            return [(${mismatchMessage})] + REGEX;
        }
        if (LENGTH != null && value.length() > LENGTH) {
            return [(${lengthMessage})] + LENGTH;
        }
        return null;
    }
}
