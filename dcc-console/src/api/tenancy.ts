import { httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type {
  ApplicationView,
  CreateApplicationBody,
  CreateEnvironmentBody,
  CreateTenantBody,
  EnvironmentView,
  TenantView,
  UpdateApplicationBody,
  UpdateEnvironmentBody,
  UpdateTenantBody,
} from '@/types/tenant'

/** 平台级租户管理（键为 code，非数字 id）。 */
export const tenantsApi = {
  list: () => httpGet<TenantView[]>('/tenants'),
  get: (code: string) => httpGet<TenantView>(`/tenants/${code}`),
  create: (body: CreateTenantBody) => httpPost<TenantView>('/tenants', body),
  update: (code: string, body: UpdateTenantBody) => httpPut<TenantView>(`/tenants/${code}`, body),
  remove: (code: string) => httpDelete(`/tenants/${code}`),
}

/** 租户内的环境（DEV / TEST / PROD）。 */
export const environmentsApi = {
  list: () => httpGet<EnvironmentView[]>('/environments'),
  get: (id: number) => httpGet<EnvironmentView>(`/environments/${id}`),
  create: (body: CreateEnvironmentBody) => httpPost<EnvironmentView>('/environments', body),
  update: (id: number, body: UpdateEnvironmentBody) =>
    httpPut<EnvironmentView>(`/environments/${id}`, body),
  remove: (id: number) => httpDelete(`/environments/${id}`),
}

/** 租户内的应用（公共基础 / 账务系统 / 客户系统）。 */
export const applicationsApi = {
  list: () => httpGet<ApplicationView[]>('/applications'),
  get: (id: number) => httpGet<ApplicationView>(`/applications/${id}`),
  create: (body: CreateApplicationBody) => httpPost<ApplicationView>('/applications', body),
  update: (id: number, body: UpdateApplicationBody) =>
    httpPut<ApplicationView>(`/applications/${id}`, body),
  remove: (id: number) => httpDelete(`/applications/${id}`),
}
