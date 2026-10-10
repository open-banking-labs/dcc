import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type { StandardStatus } from '@/types/common'
import type {
  InterfaceContent,
  InterfaceDiffView,
  InterfaceVersionView,
  InterfaceView,
} from '@/types/interface'

export interface InterfaceListParams {
  environmentId?: number
  applicationId?: number
  status?: StandardStatus
}

export interface CreateInterfaceBody {
  interfaceNo: string
  templateId?: number | null
  content: InterfaceContent
}

export interface UpdateInterfaceBody {
  templateId?: number | null
  content: InterfaceContent
}

export const interfacesApi = {
  list: (params: InterfaceListParams = {}) =>
    httpGet<InterfaceView[]>('/interfaces', { params: cleanParams(params) }),

  get: (id: number) => httpGet<InterfaceView>(`/interfaces/${id}`),

  create: (environmentId: number, applicationId: number, body: CreateInterfaceBody) =>
    httpPost<InterfaceView>('/interfaces', body, { params: { environmentId, applicationId } }),

  update: (id: number, body: UpdateInterfaceBody) =>
    httpPut<InterfaceView>(`/interfaces/${id}`, body),

  remove: (id: number) => httpDelete(`/interfaces/${id}`),

  history: (id: number) => httpGet<InterfaceVersionView[]>(`/interfaces/${id}/history`),

  diff: (id: number, from: string, to: string) =>
    httpGet<InterfaceDiffView>(`/interfaces/${id}/diff`, { params: { from, to } }),

  verify: (id: number, version: string) =>
    httpGet<boolean>(`/interfaces/${id}/versions/${version}/verify`),

  transition: (id: number, target: StandardStatus) =>
    httpPost<InterfaceView>(`/interfaces/${id}/status`, undefined, { params: { target } }),

  rollback: (id: number, version: string) =>
    httpPost<InterfaceView>(`/interfaces/${id}/rollback`, undefined, { params: { version } }),
}
