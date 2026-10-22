package cn.org.openbanking.dcc.core.id;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Builds the effective rule set: built-in defaults, overridden wholesale by any
 * {@code dcc.id.rules.<CODE>} entry, then validated once at startup.
 */
@Component
public class IdRuleRegistry {

    private final Map<String, IdRule> rules;

    public IdRuleRegistry(IdProperties properties) {
        Map<String, IdRule> merged = new LinkedHashMap<>(IdRules.defaults());
        if (properties != null && properties.getRules() != null) {
            properties.getRules().forEach((code, override) -> {
                if (code == null || code.isBlank() || override == null) {
                    return;
                }
                merged.put(code, toRule(code, override, merged.get(code)));
            });
        }
        merged.values().forEach(IdRuleRegistry::validate);
        this.rules = Collections.unmodifiableMap(merged);
    }

    public IdRule require(String code) {
        IdRule rule = rules.get(code);
        if (rule == null) {
            throw new IllegalStateException("identifier rule not configured: " + code);
        }
        return rule;
    }

    public Map<String, IdRule> all() {
        return rules;
    }

    private static IdRule toRule(String code, IdProperties.Rule source, IdRule fallback) {
        int maxLength = source.getMaxLength() != null
                ? source.getMaxLength()
                : fallback != null ? fallback.maxLength() : 128;
        List<IdPart> parts = new ArrayList<>();
        if (source.getParts() != null) {
            for (IdProperties.Part part : source.getParts()) {
                parts.add(toPart(code, part));
            }
        }
        SequenceSpec sequence = source.getSequence() == null ? null : new SequenceSpec(
                source.getSequence().getSource(),
                source.getSequence().getKey(),
                source.getSequence().getStep() == null ? 1 : source.getSequence().getStep(),
                source.getSequence().getMaxValue() == null ? 0 : source.getSequence().getMaxValue());
        return new IdRule(code, source.getPrefix(), source.getSuffix(), source.getSeparator(),
                parts, sequence, maxLength);
    }

    private static IdPart toPart(String ruleCode, IdProperties.Part source) {
        PartType type;
        try {
            type = PartType.valueOf(source.getType() == null ? "" : source.getType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "unknown part type '" + source.getType() + "' in identifier rule " + ruleCode);
        }
        ZoneId zone = null;
        if (source.getZone() != null && !source.getZone().isBlank()) {
            zone = ZoneId.of(source.getZone());
        }
        String pad = source.getPad() == null || source.getPad().isEmpty() ? "0" : source.getPad();
        return new IdPart(type, source.getValue(),
                source.getWidth() == null ? 0 : source.getWidth(),
                source.getRadix() == null ? 10 : source.getRadix(),
                pad.charAt(0),
                source.getPattern(), zone);
    }

    private static void validate(IdRule rule) {
        if (rule.parts().isEmpty()) {
            throw new IllegalStateException("identifier rule " + rule.code() + " has no parts");
        }
        for (IdPart part : rule.parts()) {
            if (part.radix() < 2 || part.radix() > 36) {
                throw new IllegalStateException("identifier rule " + rule.code() + " has radix out of range");
            }
            if (part.type() != PartType.LITERAL && part.type() != PartType.TYPE
                    && part.type() != PartType.DATE && part.width() <= 0) {
                throw new IllegalStateException("identifier rule " + rule.code()
                        + " requires a positive width for part " + part.type());
            }
            if (part.type() == PartType.DATE && (part.pattern() == null || part.pattern().isBlank())) {
                throw new IllegalStateException("identifier rule " + rule.code() + " DATE part requires a pattern");
            }
        }
        if (rule.hasSequencePart() && rule.sequence() == null) {
            throw new IllegalStateException("identifier rule " + rule.code() + " has SEQUENCE part without sequence");
        }
        if (rule.sequence() != null) {
            IdSequenceKeyResolver.validateTemplate(rule.sequence().key());
            if (rule.sequence().maxValue() <= 0) {
                throw new IllegalStateException("identifier rule " + rule.code() + " requires a positive max-value");
            }
            if (!rule.hasSequencePart()) {
                throw new IllegalStateException("identifier rule " + rule.code()
                        + " declares a sequence but has no SEQUENCE part");
            }
            for (IdPart part : rule.parts()) {
                if (part.type() == PartType.SEQUENCE) {
                    int digits = Long.toString(rule.sequence().maxValue(), part.radix()).length();
                    if (digits > part.width()) {
                        throw new IllegalStateException("identifier rule " + rule.code()
                                + " max-value does not fit the sequence width " + part.width());
                    }
                }
            }
        }
        int maxLength = rule.prefix().length() + rule.suffix().length();
        for (int i = 0; i < rule.parts().size(); i++) {
            if (i > 0) {
                maxLength += rule.separator().length();
            }
            maxLength += maxPartLength(rule.parts().get(i));
        }
        if (maxLength > rule.maxLength()) {
            throw new IllegalStateException("identifier rule " + rule.code() + " can exceed "
                    + rule.maxLength() + " characters (worst case " + maxLength + ")");
        }
    }

    private static int maxPartLength(IdPart part) {
        return switch (part.type()) {
            case LITERAL -> part.value().length();
            case TYPE -> 1;
            case TENANT, APPLICATION, SEQUENCE -> part.width();
            case DATE -> part.pattern() == null ? 0 : part.pattern().length();
        };
    }
}
