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

/** `StandardExportItem`。 */
export interface StandardExportItem {
  code: string
  category: string | null
  content: StandardContent
}

/** `DataStandardService.ImportResult`。 */
export interface StandardImportResult {
  created: number
  updated: number
}
