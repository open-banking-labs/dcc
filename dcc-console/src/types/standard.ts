import type { FieldChange, StandardStatus, VersionChangeType } from './common'

/** 数标版本内容（`StandardContent`）。 */
export interface StandardContent {
  name: string
  description: string | null
  dataType: string
  length: number | null
  scale: number | null
  required: boolean
  defaultValue: string | null
  enumValues: string | null
  regex: string | null
  exampleValue: string | null
}

/** `DataStandardService.StandardSummaryView`：列表/引用分析用的轻量行。 */
export interface StandardSummaryView {
  id: number
  environmentId: number
  applicationId: number
  code: string
  name: string | null
  category: string | null
  dataType: string | null
  status: StandardStatus
  currentVersion: string
  updatedAt: string
}

/** 服务端分页的 `Page<StandardSummaryView>`（Spring Data 序列化）。 */
export interface StandardPage {
  content: StandardSummaryView[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** `DataStandardService.StandardView`。 */
export interface StandardView {
  id: number
  tenantId: string
  environmentId: number
  applicationId: number
  code: string
  category: string | null
  status: StandardStatus
  currentVersion: string
  content: StandardContent
  createdAt: string
  updatedAt: string
}

/** `DataStandardService.VersionView`。 */
export interface StandardVersionView {
  id: number
  version: string
  changeType: VersionChangeType
  changeSummary: string | null
  content: StandardContent
  sourceEnvironmentId: number | null
  sourceVersion: string | null
  createdAt: string
  contentHash: string
  parentHash: string | null
  author: string | null
  message: string | null
}

/** `DataStandardService.DiffView`。 */
export interface StandardDiffView {
  fromVersion: string
  toVersion: string
  changeType: VersionChangeType
  changes: FieldChange[]
}

/** 导出任务状态。 */
export type StandardExportStatus = 'PENDING' | 'RUNNING' | 'READY' | 'FAILED' | 'EXPIRED'

/** `StandardExportService.ExportJobView`。 */
export interface StandardExportJob {
  id: number
  status: StandardExportStatus
  rowCount: number | null
  fileName: string | null
  downloadUrl: string | null
  expiresAt: string | null
  errorMessage: string | null
  createdAt: string
}

/** 导入时被拒绝的一行（Excel 行号 + 原因）。 */
export interface StandardImportRowError {
  row: number
  message: string
}

/** `DataStandardService.ImportResult`。 */
export interface StandardImportResult {
  created: number
  updated: number
  errors: StandardImportRowError[]
}
