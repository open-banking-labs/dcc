import { httpGet, httpPost } from '@/lib/apiClient'
import type { AuditAction, AuditEntryView, AuditVerifyView } from '@/types/audit'
import { PERMISSION } from '@/lib/permissions'

/** 动态类型参数（均为后端可选筛选项）。 */
export interface AuditSearchParams {
  entityType?: string | null
  entityId?: number | null
  actor?: string | null
  action?: AuditAction | null
  /** ISO 时间下界。 */
  from?: string | null
  /** ISO 时间上界。 */
  to?: string | null
  /** 服务端分页页码（0 起）。 */
  page?: number
  /** 服务端分页大小。 */
  size?: number
}

/** Spring Data Page 序列化（前端只取用到的字段）。 */
export interface AuditPage {
  content: AuditEntryView[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** 审计日志（管理员）。注意 audit_log 由后端按 JWT 租户过滤。 */
export const auditApi = {
  search: (params: AuditSearchParams, platform = false) =>
    httpGet<AuditPage>(platform ? '/platform/audit' : '/audit', {
      params: Object.fromEntries(
        Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
      ),
    }),
  /** 单行的完整链路，旧→新。 */
  history: (entityType: string, entityId: number) =>
    httpGet<AuditEntryView[]>(`/audit/${entityType}/${entityId}`),
  /** 校验单行哈希链是否完好。 */
  verify: (entityType: string, entityId: number) =>
    httpGet<AuditVerifyView>(`/audit/${entityType}/${entityId}/verify`),
}

/**
 * 恢复一个逻辑删除的对象（管理员）。注意租户的恢复键是 code（entityCode），
 * 其余资源都是数字 id（entityId）。
 */
const RESTORE_PATH: Record<string, (entityId: number, entityCode: string | null) => string> = {
  DataStandard: (id) => `/standards/${id}/restore`,
  TableStructure: (id) => `/tables/${id}/restore`,
  InterfaceDefinition: (id) => `/interfaces/${id}/restore`,
  InterfaceTemplate: (id) => `/templates/${id}/restore`,
  Tenant: (_id, code) => `/tenants/${code}/restore`,
  TenantEnvironment: (id) => `/environments/${id}/restore`,
  TenantApplication: (id) => `/applications/${id}/restore`,
  TenantMembership: (id) => `/directory/memberships/${id}/restore`,
  RoleAssignment: (id) => `/directory/role-assignments/${id}/restore`,
}

export function restoreAuditEntity(entityType: string, id: number, code: string | null): Promise<void> {
  const path = RESTORE_PATH[entityType]
  if (!path) {
    throw new Error(`不支持恢复的类型：${entityType}`)
  }
  return httpPost<void>(path(id, code))
}

/** 该审计对象类型是否有对应的恢复端点。 */
export function canRestore(entityType: string): boolean {
  return Boolean(RESTORE_PATH[entityType])
}

export function hasRestorePermission(entityType: string, permissions: string[]): boolean {
  if (!canRestore(entityType)) return false
  const permission = entityType === 'Tenant' ? PERMISSION.tenantAdmit
    : entityType === 'TenantEnvironment' || entityType === 'TenantApplication' ? PERMISSION.topologyWrite
    : entityType === 'TenantMembership' || entityType === 'RoleAssignment' ? PERMISSION.directoryWritePlatform
    : PERMISSION.artifactRestore
  return permissions.includes(permission)
}

