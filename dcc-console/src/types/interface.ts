import type { FieldChange, StandardStatus, VersionChangeType } from './common'

/** 接口输入/输出字段（`InterfaceField`），可引用数标或内联，可嵌套。 */
export interface InterfaceField {
  name: string
  standardId: number | null
  standardCode: string | null
  dataType: string | null
  length: number | null
  scale: number | null
  required: boolean
  list: boolean
  children: InterfaceField[]
}

/** 接口版本内容（`InterfaceContent`）。 */
export interface InterfaceContent {
  name: string
  businessModule: string | null
  url: string | null
  description: string | null
  shortName: string | null
  input: InterfaceField[]
  output: InterfaceField[]
}

/** `InterfaceService.InterfaceView`。 */
export interface InterfaceView {
  id: number
  tenantId: string
  environmentId: number
  applicationId: number
  interfaceNo: string
  templateId: number | null
  status: StandardStatus
  currentVersion: string
  content: InterfaceContent
  createdAt: string
  updatedAt: string
}

/** `InterfaceService.VersionView`。 */
export interface InterfaceVersionView {
  id: number
  version: string
  changeType: VersionChangeType
  changeSummary: string | null
  content: InterfaceContent
  sourceEnvironmentId: number | null
  sourceVersion: string | null
  createdAt: string
  contentHash: string
  parentHash: string | null
  author: string | null
  message: string | null
}

/** `InterfaceService.InterfaceDiffView`。 */
export interface InterfaceDiffView {
  fromVersion: string
  toVersion: string
  changeType: VersionChangeType
  changes: FieldChange[]
}
