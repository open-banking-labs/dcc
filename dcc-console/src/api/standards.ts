import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type {
  StandardContent,
  StandardDiffView,
  StandardExportJob,
  StandardImportResult,
  StandardPage,
  StandardSummaryView,
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

export interface StandardPageParams {
  environmentId?: number
  applicationId?: number
  keyword?: string
  category?: string
  status?: StandardStatus
  page?: number
  size?: number
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

export interface CreateStandardExportBody {
  environmentId: number
  applicationId: number
  keyword?: string | null
  category?: string | null
  status?: StandardStatus | null
  ids?: number[] | null
}

export const standardsApi = {
  /** 裸数组查询，供表结构/接口/模板表单的标准选择器使用。 */
  list: (params: StandardListParams = {}) =>
    httpGet<StandardView[]>('/standards', { params: cleanParams(params) }),

  /** 服务端分页查询（编码/名称模糊匹配）。 */
  page: (params: StandardPageParams = {}) =>
    httpGet<StandardPage>('/standards/page', { params: cleanParams(params) }),

  /** 所选数标的轻量摘要，供引用分析左栏分组。 */
  summaries: (ids: number[]) =>
    httpGet<StandardSummaryView[]>('/standards/summaries', { params: { ids: ids.join(',') } }),

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

  /** 创建异步 Excel 导出任务。 */
  createExport: (body: CreateStandardExportBody) =>
    httpPost<StandardExportJob>('/standards/exports', body),

  /** 轮询导出任务；READY 时返回新的预签名下载链接。 */
  exportJob: (id: number) => httpGet<StandardExportJob>(`/standards/exports/${id}`),

  /** 下载空白 Excel 导入模板。 */
  importTemplate: () => httpGet<Blob>('/standards/import-template', { responseType: 'blob' }),

  /** 上传编辑后的 Excel；所有行通过校验才会写入。 */
  importExcel: (environmentId: number, applicationId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return httpPost<StandardImportResult>('/standards/import-excel', form, {
      params: { environmentId, applicationId },
    })
  },
}
