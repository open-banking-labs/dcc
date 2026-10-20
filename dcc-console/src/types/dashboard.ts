import type { ArtifactType, MigrationStatus } from './migration'

/** `DashboardService.ArtifactCounts`：某一类产物在当前范围内的总量与待审量。 */
export interface ArtifactCounts {
  total: number
  pendingReview: number
}

/** `DashboardService.ArtifactSummary`：四类产物的卡片数据。 */
export interface ArtifactSummary {
  dataStandard: ArtifactCounts
  tableStructure: ArtifactCounts
  interfaces: ArtifactCounts
  interfaceTemplates: ArtifactCounts
}

/** `DashboardService.MigrationSummary`：迁移单按生命周期状态分组计数。 */
export interface MigrationSummary {
  pendingSubmission: number
  pendingApproval: number
  approved: number
  migrated: number
  rejected: number
  rolledBack: number
}

/** `DashboardService.RecentMigration`：最近迁移单的一行（够渲染够跳转）。 */
export interface RecentMigration {
  id: number
  artifactType: ArtifactType
  artifactCode: string | null
  status: MigrationStatus
  targetEnvironmentId: number
  updatedAt: string
}

/** `DashboardService.TenantSummary`：平台侧的租户注册表计数。 */
export interface TenantSummary {
  total: number
  enabled: number
}

/** `DashboardService.AuditSummary`：近 24 小时的审计条数。 */
export interface AuditSummary {
  last24h: number
}

/**
 * `GET /api/dashboard/summary` 的响应。
 *
 * 权限不足或不适用的 section 为 `null` —— 这是刻意的：「没有数据」与「无权查看」在界面上
 * 必须是两种样子，不能都渲染成 0。
 */
export interface DashboardSummary {
  tenantId: string | null
  artifacts: ArtifactSummary | null
  migrations: MigrationSummary | null
  recentMigrations: RecentMigration[] | null
  audit: AuditSummary | null
  tenants: TenantSummary | null
}
