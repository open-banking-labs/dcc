import type { ArtifactType, MigrationStatus, StandardStatus, TemplateSectionCode, VersionChangeType } from '@/types'
import type { AuditAction } from '@/types/audit'
import type { ReferenceCaseFeature } from '@/types/case'

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

/** 指导性案例的功能标签：前四项与产物类型同名，SKELETON 是环境/应用骨架范例。 */
export const CASE_FEATURE_LABEL: Record<ReferenceCaseFeature, string> = {
  DATA_STANDARD: '数标',
  TABLE_STRUCTURE: '表结构',
  INTERFACE: '接口',
  INTERFACE_TEMPLATE: '接口模板',
  SKELETON: '环境/应用骨架',
}

/** audit_log.entity_type（Hibernate 实体 SimpleName）→ 中文标签。 */
export const AUDIT_ENTITY_LABEL: Record<string, string> = {
  DataStandard: '数标',
  DataStandardVersion: '数标版本',
  TableStructure: '表结构',
  TableStructureVersion: '表结构版本',
  InterfaceDefinition: '接口',
  InterfaceVersion: '接口版本',
  InterfaceTemplate: '接口模板',
  Tenant: '租户',
  TenantEnvironment: '环境',
  TenantApplication: '应用',
  StandardReference: '标准引用',
  TenantMembership: '租户成员',
  RoleAssignment: '角色指派',
  MigrationOrder: '迁移单',
  TemplateVersion: '模板版本',
  ReferenceCase: '指导案例',
  ReferenceCaseAdoption: '案例采纳',
  RolePermission: '角色权限',
}

/** audit_log.action → 中文标签。 */
export const AUDIT_ACTION_LABEL: Record<AuditAction, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  LOGICAL_DELETE: '删除（保留）',
  RESTORE: '恢复',
}


export const TEMPLATE_SECTION_LABEL: Record<TemplateSectionCode, string> = {
  HEAD_PROTOCOL: '协议头',
  HEAD_GATEWAY: '网关头',
  HEAD_ROUTE: '路由头',
  HEAD_BUSINESS: '业务头',
  BODY: '数据体',
  TRAILER: '尾部',
}
