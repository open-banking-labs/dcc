import { apiClient, cleanParams, httpGet } from '@/lib/apiClient'
import type { ReleaseSummary } from '@/types/generation'

export const generationApi = {
  /** 环境 + 应用 → 发布清单（将打包的接口 / 数标）。 */
  manifest: (environmentId: number, applicationId: number) =>
    httpGet<ReleaseSummary>('/generation/manifest', { params: { environmentId, applicationId } }),

  /** 环境 + 应用 → OpenAPI 文档（原始 JSON 文本）。 */
  openApi: async (environmentId: number, applicationId: number): Promise<string> => {
    const { data } = await apiClient.get<string>('/generation/openapi', {
      params: { environmentId, applicationId },
      responseType: 'text',
      // 原样返回字符串，避免 axios 二次 JSON 解析。
      transformResponse: [(raw) => raw],
    })
    return data
  },

  /** 环境 + 应用 → 发布（生成并下载 JAR 二进制）。 */
  release: async (environmentId: number, applicationId: number, version?: string) => {
    const { data } = await apiClient.get<Blob>('/generation/release', {
      params: cleanParams({ environmentId, applicationId, version }),
      responseType: 'blob',
    })
    return data
  },
}
