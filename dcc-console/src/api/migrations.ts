import { cleanParams, httpGet, httpPost } from '@/lib/apiClient'
import type {
  CreateMigrationBody,
  MigrationOrderView,
  MigrationStatus,
} from '@/types/migration'

export interface MigrationListParams {
  targetEnvironmentId?: number
  status?: MigrationStatus
}

export const migrationsApi = {
  list: (params: MigrationListParams = {}) =>
    httpGet<MigrationOrderView[]>('/migrations', { params: cleanParams(params) }),

  get: (id: number) => httpGet<MigrationOrderView>(`/migrations/${id}`),

  create: (body: CreateMigrationBody) => httpPost<MigrationOrderView>('/migrations', body),

  submit: (id: number) => httpPost<MigrationOrderView>(`/migrations/${id}/submit`),

  approve: (id: number) => httpPost<MigrationOrderView>(`/migrations/${id}/approve`),

  reject: (id: number) => httpPost<MigrationOrderView>(`/migrations/${id}/reject`),

  execute: (id: number) => httpPost<MigrationOrderView>(`/migrations/${id}/execute`),

  rollback: (id: number) => httpPost<MigrationOrderView>(`/migrations/${id}/rollback`),
}
