package cn.org.openbanking.dcc.application.common;

import java.util.function.Predicate;

import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.id.CodeType;
import cn.org.openbanking.dcc.core.id.IdFactory;

import org.springframework.stereotype.Component;

/**
 * Resolves the business code of an artifact (data standard, interface, template).
 *
 * <p>A provided code is validated and used as-is; a blank one is generated from the
 * artifact's identifier rule. Generated values are checked against the target scope so
 * a legacy/manual code that happens to look generated is skipped rather than causing a
 * unique-key failure.
 */
@Component
public class ArtifactCodeService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final IdFactory idFactory;

    public ArtifactCodeService(IdFactory idFactory) {
        this.idFactory = idFactory;
    }

    public String assign(CodeType type, String tenantId, Long applicationId, String requestedCode,
            Predicate<String> exists) {
        return assign(type, tenantId, applicationId, requestedCode, false, exists);
    }

    /**
     * @param allowReservedCode true for system-propagated codes (import, migration,
     *                          reference-case adoption), where a generated-looking code
     *                          must be preserved rather than rejected as user input
     */
    public String assign(CodeType type, String tenantId, Long applicationId, String requestedCode,
            boolean allowReservedCode, Predicate<String> exists) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            idFactory.validateManualCode(type, requestedCode, allowReservedCode);
            String code = requestedCode.trim();
            if (exists.test(code)) {
                throw new ConflictException(type.name().toLowerCase() + " already exists: " + code);
            }
            return code;
        }
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = idFactory.nextArtifactCode(type, tenantId, applicationId);
            if (!exists.test(code)) {
                return code;
            }
        }
        throw new ConflictException("unable to allocate a generated code for " + type);
    }
}
