import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type {
  StandardContent,
  StandardDiffView,
  StandardExportItem,
  StandardImportResult,
  StandardVersionView,
  StandardView,
} from '@/types/standard'
import type { StandardStatus } from '@/types/common'

export interface StandardListParams {
  environmentId?: number
  applicationId?: number
  category?: string
  status?: StandardStatus
}

export interface CreateStandardBody {
  code: string
  category?: string | null
  content: StandardContent
}

export interface UpdateStandardBody {
  category?: string | null
  content: StandardContent
}

export const standardsApi = {
  list: (params: StandardListParams = {}) =>
    httpGet<StandardView[]>('/standards', { params: cleanParams(params) }),

  get: (id: number) => httpGet<StandardView>(`/standards/${id}`),

  create: (environmentId: number, applicationId: number, body: CreateStandardBody) =>
    httpPost<StandardView>('/standards', body, { params: { environmentId, applicationId } }),

  update: (id: number, body: UpdateStandardBody) => httpPut<StandardView>(`/standards/${id}`, body),

  remove: (id: number) => httpDelete(`/standards/${id}`),

  history: (id: number) => httpGet<StandardVersionView[]>(`/standards/${id}/history`),

  diff: (id: number, from: string, to: string) =>
    httpGet<StandardDiffView>(`/standards/${id}/diff`, { params: { from, to } }),

  verify: (id: number, version: string) =>
    httpGet<boolean>(`/standards/${id}/versions/${version}/verify`),

  references: (id: number) => httpGet<string[]>(`/standards/${id}/references`),

  transition: (id: number, target: StandardStatus) =>
    httpPost<StandardView>(`/standards/${id}/status`, undefined, { params: { target } }),

  rollback: (id: number, version: string) =>
    httpPost<StandardView>(`/standards/${id}/rollback`, undefined, { params: { version } }),

  exportItems: (environmentId: number, applicationId: number) =>
    httpGet<StandardExportItem[]>('/standards/export', { params: { environmentId, applicationId } }),

  importItems: (environmentId: number, applicationId: number, items: StandardExportItem[]) =>
    httpPost<StandardImportResult>('/standards/import', items, {
      params: { environmentId, applicationId },
    }),
}
