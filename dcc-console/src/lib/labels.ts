import type { ArtifactType, MigrationStatus, StandardStatus, TemplateSectionCode, VersionChangeType } from '@/types'

export const STANDARD_STATUS_LABEL: Record<StandardStatus, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审',
  PUBLISHED: '发布',
  DEPRECATED: '废弃',
}

export const MIGRATION_STATUS_LABEL: Record<MigrationStatus, string> = {
  PENDING_SUBMISSION: '待提交',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已批准',
  MIGRATED: '已迁移',
  REJECTED: '已拒绝',
  ROLLED_BACK: '已回滚',
}

export const CHANGE_TYPE_LABEL: Record<VersionChangeType, string> = {
  MAJOR: 'MAJOR',
  MINOR: 'MINOR',
  PATCH: 'PATCH',
}

export const ARTIFACT_TYPE_LABEL: Record<ArtifactType, string> = {
  DATA_STANDARD: '数标',
  TABLE_STRUCTURE: '表结构',
  INTERFACE: '接口',
  INTERFACE_TEMPLATE: '接口模板',
}

export const TEMPLATE_SECTION_LABEL: Record<TemplateSectionCode, string> = {
  HEAD_PROTOCOL: '协议头',
  HEAD_GATEWAY: '网关头',
  HEAD_ROUTE: '路由头',
  HEAD_BUSINESS: '业务头',
  BODY: '数据体',
  TRAILER: '尾部',
}
