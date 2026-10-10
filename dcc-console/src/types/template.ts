import type { FieldChange, StandardStatus, VersionChangeType } from './common'

/** 模板分层的固定编码（`TemplateSectionCode`）。 */
export type TemplateSectionCode =
  | 'HEAD_PROTOCOL'
  | 'HEAD_GATEWAY'
  | 'HEAD_ROUTE'
  | 'HEAD_BUSINESS'
  | 'BODY'
  | 'TRAILER'

/** 模板字段（`TemplateField`）。 */
export interface TemplateField {
  name: string
  standardId: number | null
  standardCode: string | null
  dataType: string | null
  length: number | null
  scale: number | null
  required: boolean
  role: string | null
  children: TemplateField[]
}

/** 模板分层（`TemplateSection`）。 */
export interface TemplateSection {
  code: TemplateSectionCode
  name: string
  fields: TemplateField[]
}

/** 模板版本内容（`TemplateContent`）。 */
export interface TemplateContent {
  name: string
  description: string | null
  sections: TemplateSection[]
}

/** `TemplateService.TemplateView`。 */
export interface TemplateView {
  id: number
  tenantId: string
  environmentId: number
  applicationId: number
  code: string
  status: StandardStatus
  currentVersion: string
  content: TemplateContent
  createdAt: string
  updatedAt: string
}

/** `TemplateService.VersionView`。 */
export interface TemplateVersionView {
  id: number
  version: string
  changeType: VersionChangeType
  changeSummary: string | null
  content: TemplateContent
  sourceEnvironmentId: number | null
  sourceVersion: string | null
  createdAt: string
  contentHash: string
  parentHash: string | null
  author: string | null
  message: string | null
}

/** `TemplateService.TemplateDiffView`。 */
export interface TemplateDiffView {
  fromVersion: string
  toVersion: string
  changeType: VersionChangeType
  changes: FieldChange[]
}
