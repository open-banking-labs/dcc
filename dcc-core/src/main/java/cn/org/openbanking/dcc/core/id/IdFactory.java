package cn.org.openbanking.dcc.core.id;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.tenant.Tenant;
import cn.org.openbanking.dcc.core.tenant.TenantApplication;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.springframework.stereotype.Component;

/**
 * The single entry point for DCC-generated identifiers.
 *
 * <p>It resolves the numeric tenant/application segments, asks the rule's
 * {@link SequenceSource} for a value, and lets {@link IdAssembler} render the final
 * string. Rules are declarative ({@link IdRule}) and sequence state is persisted, so a
 * restart continues the same global flow.
 */
@Component
public class IdFactory {

    private final IdRuleRegistry rules;
    private final IdAssembler assembler;
    private final Map<String, SequenceSource> sequenceSources;
    private final TenantRepository tenants;
    private final TenantApplicationRepository applications;

    public IdFactory(IdRuleRegistry rules,
            IdAssembler assembler,
            List<SequenceSource> sequenceSources,
            TenantRepository tenants,
            TenantApplicationRepository applications) {
        this.rules = rules;
        this.assembler = assembler;
        this.tenants = tenants;
        this.applications = applications;
        this.sequenceSources = new LinkedHashMap<>();
        for (SequenceSource source : sequenceSources) {
            this.sequenceSources.put(source.name(), source);
        }
        for (IdRule rule : rules.all().values()) {
            if (rule.sequence() != null && !this.sequenceSources.containsKey(rule.sequence().source())) {
                throw new IllegalStateException("unknown sequence source '" + rule.sequence().source()
                        + "' for identifier rule " + rule.code());
            }
        }
    }

    /** {@code T001} style tenant code. */
    public String nextTenantCode() {
        IdRule rule = rules.require(IdRules.TENANT_CODE);
        long sequence = nextSequence(rule, null, null, null);
        return assembler.assemble(rule, new IdContext(null, null, null, sequence, Instant.now()));
    }

    /**
     * Allocates a per-tenant application number and its default code.
     *
     * <p>Callers that generated a code collision should call this again to advance to
     * the next application number (a skipped number is acceptable; reuse is not).
     */
    public ApplicationIdentity nextApplication(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        IdRule numberRule = rules.require(IdRules.APPLICATION_NO);
        long number = nextSequence(numberRule, tenant.getId(), null, null);
        if (number > Integer.MAX_VALUE) {
            throw new IllegalStateException("application number exceeds int range: " + number);
        }
        IdRule codeRule = rules.require(IdRules.APPLICATION_CODE);
        String code = assembler.assemble(codeRule,
                new IdContext(CodeType.APPLICATION.code(), tenant.getId(), (int) number, null, Instant.now()));
        return new ApplicationIdentity((int) number, code);
    }

    /** {@code E001} style environment code. */
    public String nextEnvironmentCode(String tenantId) {
        Tenant tenant = requireTenant(tenantId);
        IdRule rule = rules.require(IdRules.ENVIRONMENT_CODE);
        long sequence = nextSequence(rule, tenant.getId(), null, null);
        return assembler.assemble(rule, new IdContext(null, tenant.getId(), null, sequence, Instant.now()));
    }

    /**
     * Generates the next artifact code for a data standard, interface or template.
     *
     * @param type          artifact type (its character is the type segment)
     * @param tenantId      tenant code, resolved to the numeric tenant segment
     * @param applicationId application id, resolved to its per-tenant number
     */
    public String nextArtifactCode(CodeType type, String tenantId, Long applicationId) {
        if (!type.artifact()) {
            throw new IllegalArgumentException("not an artifact code type: " + type);
        }
        Tenant tenant = requireTenant(tenantId);
        TenantApplication application = requireApplication(tenantId, applicationId);
        if (application.getAppNo() <= 0) {
            throw new IllegalStateException("application " + applicationId + " has no application number");
        }
        IdRule rule = rules.require(type.ruleCode());
        if (rule.sequence() == null) {
            throw new IllegalStateException("identifier rule " + rule.code() + " has no sequence");
        }
        String key = IdSequenceKeyResolver.resolve(rule.sequence().key(), type.code(),
                tenant.getId(), application.getAppNo());
        long sequence = source(rule.sequence()).next(key, rule.sequence().step(), rule.sequence().maxValue());
        return assembler.assemble(rule, new IdContext(type.code(), tenant.getId(),
                application.getAppNo(), sequence, Instant.now()));
    }

    /**
     * Validates a user-supplied code. System-generated formats are reserved so a manual
     * entry can never collide with a future generated value.
     */
    public void validateManualCode(CodeType type, String code) {
        validateManualCode(type, code, false);
    }

    /**
     * @param allowReservedCode true for codes propagated by the system (import, migration,
     *                          reference-case adoption); a generated-looking code must then
     *                          be preserved instead of rejected as user input
     */
    public void validateManualCode(CodeType type, String code, boolean allowReservedCode) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("code must not be blank");
        }
        String trimmed = code.trim();
        if (trimmed.length() > type.maxCodeLength()) {
            throw new ValidationException("code exceeds " + type.maxCodeLength() + " characters");
        }
        if (!allowReservedCode && type.artifact() && Pattern.matches(
                "^" + Pattern.quote(String.valueOf(type.code())) + "[0-9]{14}$", trimmed)) {
            throw new ValidationException("code '" + trimmed + "' is reserved for system-generated identifiers");
        }
    }

    /** @return persisted positions of the sequences whose key starts with {@code prefix}. */
    public List<SequenceSnapshot> snapshots(String prefix) {
        List<SequenceSnapshot> merged = new ArrayList<>();
        for (SequenceSource source : sequenceSources.values()) {
            merged.addAll(source.snapshots(prefix));
        }
        merged.sort(Comparator.comparing(SequenceSnapshot::key));
        return merged;
    }

    private long nextSequence(IdRule rule, Long tenantId, Integer applicationNo, Character type) {
        if (rule.sequence() == null) {
            throw new IllegalStateException("identifier rule " + rule.code() + " has no sequence");
        }
        String key = IdSequenceKeyResolver.resolve(rule.sequence().key(), type, tenantId, applicationNo);
        return source(rule.sequence()).next(key, rule.sequence().step(), rule.sequence().maxValue());
    }

    private SequenceSource source(SequenceSpec spec) {
        SequenceSource source = sequenceSources.get(spec.source());
        if (source == null) {
            throw new IllegalStateException("sequence source not available: " + spec.source());
        }
        return source;
    }

    private Tenant requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResourceNotFoundException("tenant is required for identifier generation");
        }
        return tenants.findByCode(tenantId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tenant", "code", tenantId));
    }

    private TenantApplication requireApplication(String tenantId, Long applicationId) {
        if (applicationId == null) {
            throw new ResourceNotFoundException("application is required for identifier generation");
        }
        return applications.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("TenantApplication", applicationId));
    }

    /** Result of allocating an application: its per-tenant number and default code. */
    public record ApplicationIdentity(int number, String code) {
    }
}
