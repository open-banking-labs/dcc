import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type { StandardStatus } from '@/types/common'
import type {
  TableContent,
  TableDdl,
  TableDiffView,
  TableVersionView,
  TableView,
} from '@/types/table'

export interface TableListParams {
  environmentId?: number
  applicationId?: number
  status?: StandardStatus
}

export interface CreateTableBody {
  code: string
  content: TableContent
}

export interface UpdateTableBody {
  content: TableContent
}

export const tablesApi = {
  list: (params: TableListParams = {}) =>
    httpGet<TableView[]>('/tables', { params: cleanParams(params) }),

  get: (id: number) => httpGet<TableView>(`/tables/${id}`),

  create: (environmentId: number, applicationId: number, body: CreateTableBody) =>
    httpPost<TableView>('/tables', body, { params: { environmentId, applicationId } }),

  update: (id: number, body: UpdateTableBody) => httpPut<TableView>(`/tables/${id}`, body),

  remove: (id: number) => httpDelete(`/tables/${id}`),

  history: (id: number) => httpGet<TableVersionView[]>(`/tables/${id}/history`),

  diff: (id: number, from: string, to: string) =>
    httpGet<TableDiffView>(`/tables/${id}/diff`, { params: { from, to } }),

  verify: (id: number, version: string) =>
    httpGet<boolean>(`/tables/${id}/versions/${version}/verify`),

  transition: (id: number, target: StandardStatus) =>
    httpPost<TableView>(`/tables/${id}/status`, undefined, { params: { target } }),

  rollback: (id: number, version: string) =>
    httpPost<TableView>(`/tables/${id}/rollback`, undefined, { params: { version } }),

  ddl: (id: number, schema?: string) =>
    httpGet<TableDdl>(`/tables/${id}/ddl`, { params: cleanParams({ schema }) }),

  alterDdl: (id: number, from: string, to: string, schema?: string) =>
    httpGet<TableDdl>(`/tables/${id}/ddl/alter`, { params: cleanParams({ from, to, schema }) }),
}
