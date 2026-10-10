import type { FieldChange, StandardStatus, VersionChangeType } from './common'

/** 表列（`TableColumn`）。引用数标时类型字段由后端解析并快照。 */
export interface TableColumn {
  columnName: string
  standardId: number | null
  standardCode: string | null
  dataType: string | null
  length: number | null
  scale: number | null
  nullable: boolean
  primaryKey: boolean
  defaultValue: string | null
  sortOrder: number
  comment: string | null
}

/** 表索引（`TableIndex`）。 */
export interface TableIndex {
  name: string
  unique: boolean
  columns: string[]
}

/** 表结构版本内容（`TableContent`）。 */
export interface TableContent {
  name: string
  description: string | null
  shardKey: string | null
  columns: TableColumn[]
  indexes: TableIndex[]
}

/** `TableStructureService.TableView`。 */
export interface TableView {
  id: number
  tenantId: string
  environmentId: number
  applicationId: number
  code: string
  status: StandardStatus
  currentVersion: string
  content: TableContent
  createdAt: string
  updatedAt: string
}

/** `TableStructureService.VersionView`。 */
export interface TableVersionView {
  id: number
  version: string
  changeType: VersionChangeType
  changeSummary: string | null
  content: TableContent
  sourceEnvironmentId: number | null
  sourceVersion: string | null
  createdAt: string
  contentHash: string
  parentHash: string | null
  author: string | null
  message: string | null
}

/** 列变更类型（`ChangeKind`）。 */
export type ChangeKind = 'ADDED' | 'REMOVED' | 'MODIFIED'

/** 列级变更（`ColumnChange`）。 */
export interface ColumnChange {
  columnName: string
  kind: ChangeKind
  fields: FieldChange[]
}

/** 表结构变更集（`TableChangeSet`）。 */
export interface TableChangeSet {
  columns: ColumnChange[]
  tableFields: FieldChange[]
  indexFields: FieldChange[]
}

/** `TableStructureService.TableDiffView`。 */
export interface TableDiffView {
  fromVersion: string
  toVersion: string
  changeType: VersionChangeType
  changes: TableChangeSet
}

/** 生成的 DDL（`TableDdl`）。 */
export interface TableDdl {
  dialect: string
  ddl: string
  flywayFileName: string
  flywayScript: string
}
