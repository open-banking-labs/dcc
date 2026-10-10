/** 后端统一错误信封（`ApiExceptionHandler.ApiError`）。 */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
}

/** 版本变更级别（项目 SemVer 规则）。 */
export type VersionChangeType = 'MAJOR' | 'MINOR' | 'PATCH'

/** 生命周期状态：数标 / 表结构 / 接口 / 模板共用。 */
export type StandardStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'DEPRECATED'

/** 单字段变更（值已渲染为文本）。 */
export interface FieldChange {
  field: string
  before: string | null
  after: string | null
}
