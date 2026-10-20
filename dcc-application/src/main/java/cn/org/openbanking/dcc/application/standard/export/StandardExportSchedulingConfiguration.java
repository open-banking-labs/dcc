package cn.org.openbanking.dcc.application.standard.export;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} only where the export worker is enabled. The audit
 * sweep may also enable scheduling; both annotations are idempotent.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dcc.export", name = "worker-enabled", havingValue = "true")
@EnableScheduling
public class StandardExportSchedulingConfiguration {
}
