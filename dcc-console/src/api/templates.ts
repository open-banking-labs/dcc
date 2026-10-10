import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type { StandardStatus } from '@/types/common'
import type {
  TemplateContent,
  TemplateDiffView,
  TemplateVersionView,
  TemplateView,
} from '@/types/template'

export interface TemplateListParams {
  environmentId?: number
  applicationId?: number
  status?: StandardStatus
}

export interface CreateTemplateBody {
  code: string
  content: TemplateContent
}

export interface UpdateTemplateBody {
  content: TemplateContent
}

export const templatesApi = {
  list: (params: TemplateListParams = {}) =>
    httpGet<TemplateView[]>('/templates', { params: cleanParams(params) }),

  get: (id: number) => httpGet<TemplateView>(`/templates/${id}`),

  create: (environmentId: number, applicationId: number, body: CreateTemplateBody) =>
    httpPost<TemplateView>('/templates', body, { params: { environmentId, applicationId } }),

  update: (id: number, body: UpdateTemplateBody) =>
    httpPut<TemplateView>(`/templates/${id}`, body),

  remove: (id: number) => httpDelete(`/templates/${id}`),

  history: (id: number) => httpGet<TemplateVersionView[]>(`/templates/${id}/history`),

  diff: (id: number, from: string, to: string) =>
    httpGet<TemplateDiffView>(`/templates/${id}/diff`, { params: { from, to } }),

  verify: (id: number, version: string) =>
    httpGet<boolean>(`/templates/${id}/versions/${version}/verify`),

  transition: (id: number, target: StandardStatus) =>
    httpPost<TemplateView>(`/templates/${id}/status`, undefined, { params: { target } }),

  rollback: (id: number, version: string) =>
    httpPost<TemplateView>(`/templates/${id}/rollback`, undefined, { params: { version } }),
}
