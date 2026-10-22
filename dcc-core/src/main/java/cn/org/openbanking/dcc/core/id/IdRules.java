package cn.org.openbanking.dcc.core.id;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in identifier rules. They reproduce DCC's stock formats:
 *
 * <ul>
 *   <li>tenant code {@code T001}</li>
 *   <li>per-tenant application number {@code 001} and code {@code A001}</li>
 *   <li>environment code {@code E001}</li>
 *   <li>artifact codes {@code S00100200000001} / {@code I...} / {@code M...}</li>
 * </ul>
 */
public final class IdRules {

    public static final String TENANT_CODE = "TENANT_CODE";
    public static final String APPLICATION_NO = "APPLICATION_NO";
    public static final String APPLICATION_CODE = "APPLICATION_CODE";
    public static final String ENVIRONMENT_CODE = "ENVIRONMENT_CODE";

    public static final int TENANT_MAX = 999;
    public static final int APPLICATION_MAX = 999;
    public static final int ENVIRONMENT_MAX = 999;
    public static final long ARTIFACT_SEQUENCE_MAX = 99_999_999L;

    private IdRules() {
    }

    public static Map<String, IdRule> defaults() {
        Map<String, IdRule> rules = new LinkedHashMap<>();

        rules.put(TENANT_CODE, new IdRule(TENANT_CODE, "T", "", "", List.of(IdPart.sequence(3)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT, "tenant-code", 1, TENANT_MAX), 64));

        rules.put(APPLICATION_NO, new IdRule(APPLICATION_NO, "", "", "", List.of(IdPart.sequence(3)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT, "application-no:{tenant}", 1, APPLICATION_MAX), 64));

        rules.put(APPLICATION_CODE, new IdRule(APPLICATION_CODE, "A", "", "", List.of(IdPart.application(3)),
                null, 64));

        rules.put(ENVIRONMENT_CODE, new IdRule(ENVIRONMENT_CODE, "E", "", "", List.of(IdPart.sequence(3)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT, "environment-code:{tenant}", 1, ENVIRONMENT_MAX), 32));

        for (CodeType type : List.of(CodeType.DATA_STANDARD, CodeType.INTERFACE, CodeType.TEMPLATE)) {
            rules.put(type.ruleCode(), artifactRule(type));
        }
        return rules;
    }

    private static IdRule artifactRule(CodeType type) {
        return new IdRule(type.ruleCode(), "", "", "",
                List.of(IdPart.typePart(), IdPart.tenant(3), IdPart.application(3), IdPart.sequence(8)),
                new SequenceSpec(SequenceSpec.DB_SEGMENT,
                        "artifact:{type}:{tenant}:{application}", 1, ARTIFACT_SEQUENCE_MAX),
                type.maxCodeLength());
    }
}
