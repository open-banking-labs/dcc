package cn.org.openbanking.dcc.core.id;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Renders a rule into its final string: {@code prefix + join(separator, parts) + suffix}.
 *
 * <p>Numeric segments are zero-padded to their configured width. A value that does not
 * fit its width is a configuration/limit error and is rejected rather than truncated,
 * because truncation would turn distinct sequence values into the same identifier.
 */
@Component
public class IdAssembler {

    public String assemble(IdRule rule, IdContext context) {
        if (rule.parts().isEmpty()) {
            throw new IllegalStateException("identifier rule '" + rule.code() + "' has no parts");
        }
        List<String> rendered = new ArrayList<>(rule.parts().size());
        for (IdPart part : rule.parts()) {
            rendered.add(render(part, context));
        }
        return rule.prefix() + String.join(rule.separator(), rendered) + rule.suffix();
    }

    private String render(IdPart part, IdContext context) {
        return switch (part.type()) {
            case LITERAL -> part.value();
            case TYPE -> {
                if (context.type() == null) {
                    throw new IllegalStateException("TYPE part requires a code type");
                }
                yield String.valueOf(context.type());
            }
            case TENANT -> format(context.tenantId(), part);
            case APPLICATION -> format(context.applicationNo(), part);
            case SEQUENCE -> format(context.sequenceValue(), part);
            case DATE -> {
                String pattern = part.pattern();
                if (pattern == null || pattern.isBlank()) {
                    throw new IllegalStateException("DATE part requires a pattern");
                }
                Instant now = context.now() == null ? Instant.now() : context.now();
                yield DateTimeFormatter.ofPattern(pattern)
                        .withZone(part.zone() == null ? ZoneOffset.UTC : part.zone())
                        .format(now);
            }
        };
    }

    private String format(Number value, IdPart part) {
        if (value == null) {
            throw new IllegalStateException("part " + part.type() + " has no value");
        }
        String rendered = Long.toString(value.longValue(), part.radix()).toUpperCase(Locale.ROOT);
        if (part.width() <= 0) {
            return rendered;
        }
        if (rendered.length() > part.width()) {
            throw new IllegalStateException("value " + rendered + " exceeds width " + part.width());
        }
        return String.valueOf(part.pad()).repeat(part.width() - rendered.length()) + rendered;
    }
}
