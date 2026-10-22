package cn.org.openbanking.dcc.core.id;

/**
 * Identifier families handled by the {@link IdFactory}.
 *
 * <p>The one-character {@code code} is the type segment of a generated business
 * identifier; the {@code ruleCode} names the assembly rule in {@code dcc.id.rules}.
 */
public enum CodeType {

    TENANT('T', "TENANT_CODE", 64),
    APPLICATION('A', "APPLICATION_CODE", 64),
    ENVIRONMENT('E', "ENVIRONMENT_CODE", 32),
    DATA_STANDARD('S', "DATA_STANDARD", 128),
    INTERFACE('I', "INTERFACE", 64),
    TEMPLATE('M', "TEMPLATE", 128);

    private final char code;
    private final String ruleCode;
    private final int maxCodeLength;

    CodeType(char code, String ruleCode, int maxCodeLength) {
        this.code = code;
        this.ruleCode = ruleCode;
        this.maxCodeLength = maxCodeLength;
    }

    public char code() {
        return code;
    }

    public String ruleCode() {
        return ruleCode;
    }

    public int maxCodeLength() {
        return maxCodeLength;
    }

    /** @return true for the artifact codes (data standard / interface / template). */
    public boolean artifact() {
        return this == DATA_STANDARD || this == INTERFACE || this == TEMPLATE;
    }
}
