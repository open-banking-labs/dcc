package cn.org.openbanking.dcc.core.id;

import java.time.ZoneId;

/**
 * One segment of an identifier rule.
 *
 * <p>{@code width}, {@code radix} and {@code pad} apply to numeric segments
 * ({@code TENANT}, {@code APPLICATION}, {@code SEQUENCE}); {@code value} applies to
 * {@code LITERAL}; {@code pattern}/{@code zone} apply to {@code DATE}.
 */
public record IdPart(
        PartType type,
        String value,
        int width,
        int radix,
        char pad,
        String pattern,
        ZoneId zone) {

    public IdPart {
        value = value == null ? "" : value;
        radix = radix == 0 ? 10 : radix;
        pad = pad == '\0' ? '0' : pad;
    }

    public static IdPart literal(String value) {
        return new IdPart(PartType.LITERAL, value, 0, 10, '0', null, null);
    }

    public static IdPart typePart() {
        return new IdPart(PartType.TYPE, "", 1, 10, '0', null, null);
    }

    public static IdPart tenant(int width) {
        return numeric(PartType.TENANT, width);
    }

    public static IdPart application(int width) {
        return numeric(PartType.APPLICATION, width);
    }

    public static IdPart sequence(int width) {
        return numeric(PartType.SEQUENCE, width);
    }

    public static IdPart date(String pattern, ZoneId zone) {
        return new IdPart(PartType.DATE, "", 0, 10, '0', pattern, zone);
    }

    private static IdPart numeric(PartType type, int width) {
        return new IdPart(type, "", width, 10, '0', null, null);
    }
}
