import { cleanParams, httpGet } from '@/lib/apiClient'
import type { DashboardSummary } from '@/types/dashboard'

export interface DashboardParams {
  /** 当前环境（迁移单按目标环境过滤，与 /api/migrations?targetEnvironmentId 一致）。 */
  environmentId?: number | null
  /** 当前应用。 */
  applicationId?: number | null
}

/**
 * 工作台聚合读。一次请求拿回所有卡片，权限不足的 section 由服务端返回 null。
 */
export const dashboardApi = {
  summary: (params: DashboardParams = {}) =>
    httpGet<DashboardSummary>('/dashboard/summary', { params: cleanParams(params) }),
}
