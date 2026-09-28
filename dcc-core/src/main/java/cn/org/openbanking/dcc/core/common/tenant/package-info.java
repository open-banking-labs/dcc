/**
 * Declares the single, global Hibernate row-level filter used for tenant
 * isolation. Entities reference it by name via
 * {@code @Filter(name = TenantFilter.NAME, condition = "tenant_id = :tenantId")}.
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
package cn.org.openbanking.dcc.core.common.tenant;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
