/** 指导性案例（ADR 0005 §D7）。feature 与产物类型同名，SKELETON 是环境/应用骨架范例。 */
export type ReferenceCaseFeature =
  | 'DATA_STANDARD'
  | 'TABLE_STRUCTURE'
  | 'INTERFACE'
  | 'INTERFACE_TEMPLATE'
  | 'SKELETON'

export interface CaseView {
  id: number
  feature: ReferenceCaseFeature
  code: string
  name: string
  description: string | null
  content: string
  caseVersion: string
  enabled: boolean
}

export interface CreateCaseBody {
  feature: ReferenceCaseFeature
  code: string
  name: string
  description?: string | null
  content: string
  caseVersion?: string | null
  enabled?: boolean | null
}

export interface UpdateCaseBody {
  name?: string | null
  description?: string | null
  content?: string | null
  caseVersion?: string | null
  enabled?: boolean | null
}

/** 采纳请求：内容类案例需要目标环境/应用；INTERFACE 还需要 templateId。 */
export interface AdoptCaseBody {
  environmentId?: number | null
  applicationId?: number | null
  templateId?: number | null
  code?: string | null
}

export interface AdoptResult {
  feature: ReferenceCaseFeature
  adoptedFrom: string
  created: string[]
}

/** 租户视角：我的副本，stale 表示案例已升级（副本不会自动跟随）。 */
export interface CaseAdoptionView {
  id: number
  caseId: number
  caseCode: string
  adoptedCaseVersion: string
  currentCaseVersion: string | null
  stale: boolean
  artifactType: ReferenceCaseFeature
  artifactId: number | null
  artifactCode: string | null
  adoptedBy: string | null
  adoptedAt: string
}

/** 平台视角：案例的影响面，不含租户产物标识。 */
export interface CaseReachView {
  tenantId: string
  adoptedCaseVersion: string
  currentCaseVersion: string
  stale: boolean
  adoptedBy: string | null
  adoptedAt: string
}
