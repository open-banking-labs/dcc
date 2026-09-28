package cn.org.openbanking.dcc.application.common;

import cn.org.openbanking.dcc.core.common.tenant.CurrentTenant;
import cn.org.openbanking.dcc.core.common.tenant.TenantFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defence-in-depth tenant isolation: enables the Hibernate {@code tenantFilter}
 * on the session before a repository call, so even a query that forgot an explicit
 * {@code tenantId} predicate is scoped to {@link CurrentTenant}.
 *
 * <p>The <em>guarantee</em> is the explicit {@code tenantId} predicate in every
 * repository query; this aspect is a second line of defence. It no-ops safely when
 * no session/transaction is bound (a repository call outside a transaction), so it
 * can never break a request.
 */
@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* cn.org.openbanking.dcc.core..repository..*(..))")
    public Object scopeToCurrentTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = CurrentTenant.get();
        if (tenantId != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            enableFilter(tenantId);
        }
        return joinPoint.proceed();
    }

    private void enableFilter(String tenantId) {
        try {
            Session session = entityManager.unwrap(Session.class);
            if (session.getEnabledFilter(TenantFilter.NAME) == null) {
                session.enableFilter(TenantFilter.NAME).setParameter(TenantFilter.PARAM, tenantId);
            }
        } catch (RuntimeException ignored) {
            // No Hibernate session bound yet; the explicit tenant predicate still isolates.
        }
    }
}
