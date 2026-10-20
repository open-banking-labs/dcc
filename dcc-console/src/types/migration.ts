/** 可迁移的产物类型（`ArtifactType`）。 */
export type ArtifactType = 'DATA_STANDARD' | 'TABLE_STRUCTURE' | 'INTERFACE' | 'INTERFACE_TEMPLATE'

/** 迁移单状态机（`MigrationStatus`）。 */
export type MigrationStatus =
  | 'PENDING_SUBMISSION'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'MIGRATED'
  | 'REJECTED'
  | 'ROLLED_BACK'

/** `MigrationService.MigrationOrderView`。 */
export interface MigrationOrderView {
  id: number
  tenantId: string
  sourceEnvironmentId: number
  targetEnvironmentId: number
  artifactType: ArtifactType
  artifactId: number
  artifactCode: string
  sourceVersion: string
  status: MigrationStatus
  note: string | null
  targetArtifactId: number | null
  targetVersion: string | null
  submittedBy: string | null
  createdAt: string
  updatedAt: string
}

/** `MigrationService.CreateCommand`。 */
export interface CreateMigrationBody {
  artifactType: ArtifactType
  artifactId: number
  sourceEnvironmentId: number
  targetEnvironmentId: number
  note?: string | null
}

/** `MigrationService.ApplicationMigrationCommand`。 */
export interface CreateApplicationMigrationBody {
  sourceEnvironmentId: number
  targetEnvironmentId: number
  applicationId: number
  /** 可选，限定产物类型；缺省为全部四类。 */
  artifactTypes?: ArtifactType[] | null
  note?: string | null
}

/** `MigrationService.ApplicationMigrationResult`。 */
export interface ApplicationMigrationResult {
  created: MigrationOrderView[]
  skipped: string[]
}
