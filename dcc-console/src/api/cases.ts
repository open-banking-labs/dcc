import { cleanParams, httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type {
  AdoptCaseBody,
  AdoptResult,
  CaseAdoptionView,
  CaseReachView,
  CaseView,
  CreateCaseBody,
  ReferenceCaseFeature,
  UpdateCaseBody,
} from '@/types/case'

/** 指导性案例（ADR 0005 §D7）。读取/采纳是租户侧，编写是平台侧。 */
export const casesApi = {
  list: (params: { feature?: ReferenceCaseFeature; adoptableOnly?: boolean } = {}) =>
    httpGet<CaseView[]>('/cases', { params: cleanParams(params) }),

  get: (id: number) => httpGet<CaseView>(`/cases/${id}`),

  create: (body: CreateCaseBody) => httpPost<CaseView>('/cases', body),

  update: (id: number, body: UpdateCaseBody) => httpPut<CaseView>(`/cases/${id}`, body),

  remove: (id: number) => httpDelete(`/cases/${id}`),

  restore: (id: number) => httpPost<CaseView>(`/cases/${id}/restore`),

  adopt: (id: number, body: AdoptCaseBody) => httpPost<AdoptResult>(`/cases/${id}/adopt`, body),

  /** 本租户的采纳记录（含 stale 提示）。 */
  myAdoptions: () => httpGet<CaseAdoptionView[]>('/case-adoptions'),

  /** 某个案例的影响面（平台侧，不含租户产物标识）。 */
  reach: (caseId: number) => httpGet<CaseReachView[]>(`/platform/case-adoptions/${caseId}`),
}
