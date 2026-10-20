/**
 * 权限点（后端 `@perms.*` 的 authority 字符串，ADR 0005）。
 *
 * 端点判定的是权限点，角色只是配置里的权限包；因此 UI 一律比对权限点，绝不比对角色名。
 */
export const PERMISSION = {
  artifactRead: 'artifact.read',
  artifactWrite: 'artifact.write',
  artifactDelete: 'artifact.delete',
  artifactRestore: 'artifact.restore',
  artifactTransition: 'artifact.transition',
  migrationWrite: 'migration.write',
  migrationApprove: 'migration.approve',
  migrationExecute: 'migration.execute',
  generationRead: 'generation.read',
  generationPackage: 'generation.package',
  topologyRead: 'topology.read',
  topologyWrite: 'topology.write',
  directoryRead: 'directory.read',
  directoryWrite: 'directory.write',
  directoryWritePlatform: 'directory.write.platform',
  auditBusiness: 'audit.business',
  auditPlatform: 'audit.platform',
  caseRead: 'case.read',
  caseAdopt: 'case.adopt',
  caseWrite: 'case.write',
  announcementWrite: 'announcement.write',
  announcementWritePlatform: 'announcement.write.platform',
  tenantAdmit: 'tenant.admit',
} as const

/**
 * JWT 角色 → 后端 authority 的默认映射（与 DccSecurityProperties 默认一致）。
 *
 * 仅用于本地开发 Token 生成与展示；准入判断请使用 {@link PERMISSION}。
 */
export const AUTHORITY = {
  platformAdmin: 'DCC_PLATFORM_ADMIN',
  tenantAdmin: 'DCC_TENANT_ADMIN',
  envAdmin: 'DCC_ENV_ADMIN',
  maintainer: 'DCC_MAINTAINER',
  approver: 'DCC_APPROVER',
  reader: 'DCC_READER',
} as const
