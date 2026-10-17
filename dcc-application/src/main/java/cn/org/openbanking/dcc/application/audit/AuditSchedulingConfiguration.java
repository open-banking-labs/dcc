package cn.org.openbanking.dcc.application.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} execution only when the audit retention sweep is turned on
 * ({@code dcc.audit.sweep-enabled=true}), so applications that do not want scheduled work
 * at all are unaffected.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dcc.audit", name = "sweep-enabled", havingValue = "true")
@EnableScheduling
public class AuditSchedulingConfiguration {

}
