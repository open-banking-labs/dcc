import { apiClient, cleanParams, httpGet } from '@/lib/apiClient'
import type { GeneratedSource } from '@/types/generation'

export const generationApi = {
  /** 数标 → 校验类源码。 */
  validation: (standardId: number) =>
    httpGet<GeneratedSource>(`/generation/standards/${standardId}/validation`),

  /** 接口 → DTO 源码（可能多个文件）。 */
  dto: (interfaceId: number) => httpGet<GeneratedSource[]>(`/generation/interfaces/${interfaceId}/dto`),

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

  /** 环境 + 应用 → 生成的 JAR 二进制（用于浏览器下载）。 */
  downloadJar: async (environmentId: number, applicationId: number, version?: string) => {
    const { data } = await apiClient.get<Blob>('/generation/jar', {
      params: cleanParams({ environmentId, applicationId, version }),
      responseType: 'blob',
    })
    return data
  },
}
