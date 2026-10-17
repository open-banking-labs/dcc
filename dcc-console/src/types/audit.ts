/** `AuditService.AuditView` —— audit_log 的只读快照。 */
export type AuditAction = 'CREATE' | 'UPDATE' | 'LOGICAL_DELETE' | 'RESTORE'

export interface AuditEntryView {
  id: number
  tenantId: string | null
  entityType: string
  entityId: number
  entityCode: string | null
  action: AuditAction
  actor: string
  reason: string | null
  /** 变更字段列表（JSON 数组文本）。 */
  changedFields: string | null
  beforeState: string | null
  afterState: string | null
  traceId: string | null
  previousHash: string | null
  hash: string
  occurredAt: string
}

/** `AuditService.VerifyView` —— 单行哈希链校验结果。 */
export interface AuditVerifyView {
  intact: boolean
  problems: string[]
}
