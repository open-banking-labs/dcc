package cn.org.openbanking.dcc.core.id;

import java.util.List;

/**
 * A declarative identifier assembly rule: {@code prefix + joined parts + suffix}.
 *
 * <p>Rules are frozen at startup. Changing a rule (especially its prefix or width)
 * requires a restart so one sequence never produces two physical formats at once.
 */
public record IdRule(
        String code,
        String prefix,
        String suffix,
        String separator,
        List<IdPart> parts,
        SequenceSpec sequence,
        int maxLength) {

    public IdRule {
        prefix = prefix == null ? "" : prefix;
        suffix = suffix == null ? "" : suffix;
        separator = separator == null ? "" : separator;
        parts = parts == null ? List.of() : List.copyOf(parts);
        maxLength = maxLength <= 0 ? 128 : maxLength;
    }

    public boolean hasSequencePart() {
        return parts.stream().anyMatch(part -> part.type() == PartType.SEQUENCE);
    }
}
