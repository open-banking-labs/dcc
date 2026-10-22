package cn.org.openbanking.dcc.application.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import cn.org.openbanking.dcc.core.common.error.ValidationException;
import cn.org.openbanking.dcc.core.id.CodeType;
import cn.org.openbanking.dcc.core.id.IdAssembler;
import cn.org.openbanking.dcc.core.id.IdFactory;
import cn.org.openbanking.dcc.core.id.IdProperties;
import cn.org.openbanking.dcc.core.id.IdRuleRegistry;
import cn.org.openbanking.dcc.core.id.SequenceSnapshot;
import cn.org.openbanking.dcc.core.id.SequenceSource;
import cn.org.openbanking.dcc.core.id.SequenceSpec;
import cn.org.openbanking.dcc.core.tenant.Tenant;
import cn.org.openbanking.dcc.core.tenant.TenantApplication;
import cn.org.openbanking.dcc.core.tenant.repository.TenantApplicationRepository;
import cn.org.openbanking.dcc.core.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;

class IdFactoryTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantApplicationRepository applications = mock(TenantApplicationRepository.class);
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    private IdFactory factory() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(1L);
        when(tenant.getCode()).thenReturn("T001");
        when(tenants.findByCode("T001")).thenReturn(Optional.of(tenant));

        TenantApplication application = mock(TenantApplication.class);
        when(application.getId()).thenReturn(10L);
        when(application.getAppNo()).thenReturn(2);
        when(applications.findByTenantIdAndId("T001", 10L)).thenReturn(Optional.of(application));

        SequenceSource source = new SequenceSource() {
            @Override
            public String name() {
                return SequenceSpec.DB_SEGMENT;
            }

            @Override
            public long next(String key, int step, long maxValue) {
                return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
            }

            @Override
            public SequenceSnapshot snapshot(String key) {
                return new SequenceSnapshot(key, 0, 0, 1, Instant.EPOCH);
            }

            @Override
            public List<SequenceSnapshot> snapshots(String prefix) {
                return List.of();
            }
        };
        return new IdFactory(new IdRuleRegistry(new IdProperties()), new IdAssembler(), List.of(source),
                tenants, applications);
    }

    @Test
    void generatesTenantApplicationAndArtifactIdentifiers() {
        IdFactory factory = factory();

        assertThat(factory.nextTenantCode()).isEqualTo("T001");
        assertThat(factory.nextApplication("T001").code()).isEqualTo("A001");
        assertThat(factory.nextArtifactCode(CodeType.DATA_STANDARD, "T001", 10L))
                .isEqualTo("S00100200000001");
        assertThat(factory.nextArtifactCode(CodeType.INTERFACE, "T001", 10L))
                .isEqualTo("I00100200000001");
    }

    @Test
    void rejectsAReservedGeneratedFormatButAcceptsAManualCode() {
        IdFactory factory = factory();

        factory.validateManualCode(CodeType.DATA_STANDARD, "CUST_NAME");

        assertThatThrownBy(() -> factory.validateManualCode(CodeType.DATA_STANDARD, "S00100200000001"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved");

        // import/migration must be able to preserve an already generated-looking code
        factory.validateManualCode(CodeType.DATA_STANDARD, "S00100200000001", true);
    }

    @Test
    void advancesToTheNextApplicationNumber() {
        IdFactory factory = factory();

        assertThat(factory.nextApplication("T001").number()).isEqualTo(1);
        assertThat(factory.nextApplication("T001").number()).isEqualTo(2);
    }
}
