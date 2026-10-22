package cn.org.openbanking.dcc.core.id;

/**
 * Resolves the sequence-key template of a rule.
 *
 * <p>Only three placeholders are understood: {@code {type}}, {@code {tenant}} and
 * {@code {application}}. Keeping the grammar tiny avoids SpEL/script injection and
 * makes the persisted sequence keys predictable and inspectable.
 */
public final class IdSequenceKeyResolver {

    private IdSequenceKeyResolver() {
    }

    public static String resolve(String template, Character type, Long tenantId, Integer applicationNo) {
        String key = template
                .replace("{type}", type == null ? "" : String.valueOf(type))
                .replace("{tenant}", tenantId == null ? "" : String.valueOf(tenantId))
                .replace("{application}", applicationNo == null ? "" : String.valueOf(applicationNo));
        if (key.indexOf('{') >= 0 || key.indexOf('}') >= 0) {
            throw new IllegalStateException("unknown sequence-key placeholder in template: " + template);
        }
        return key;
    }

    public static void validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalStateException("sequence key template must not be blank");
        }
        String stripped = template
                .replace("{type}", "")
                .replace("{tenant}", "")
                .replace("{application}", "");
        if (stripped.indexOf('{') >= 0 || stripped.indexOf('}') >= 0) {
            throw new IllegalStateException("unknown sequence-key placeholder in template: " + template);
        }
    }
}
