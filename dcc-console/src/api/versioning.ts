import { httpGet, httpPost } from '@/lib/apiClient'
import type { FieldChange } from '@/types/common'
import type { ArtifactType } from '@/types/migration'
import type { BumpSuggestion, DriftFinding } from '@/types/versioning'

export const versioningApi = {
  /** {@code dcc hash}：存储版本的哈希。 */
  hash: (type: ArtifactType, id: number, version: string) =>
    httpGet<string>(`/versioning/${type}/${id}/hash`, { params: { version } }),

  /** {@code dcc hash}：原始 JSON 内容的哈希（请求体即模型 JSON）。 */
  hashContent: (type: ArtifactType, json: string) => httpPost<string>(`/versioning/${type}/hash`, json),

  /** {@code dcc diff}：两个版本间的人类可读变更列表。 */
  diff: (type: ArtifactType, id: number, from: string, to: string) =>
    httpGet<FieldChange[]>(`/versioning/${type}/${id}/diff`, { params: { from, to } }),

  /** {@code dcc bump}：两个版本间建议的 SemVer 级别。 */
  bump: (type: ArtifactType, id: number, from: string, to: string) =>
    httpGet<BumpSuggestion>(`/versioning/${type}/${id}/bump`, { params: { from, to } }),

  /** 漂移检查：范围内快照不再校验通过的产物。 */
  drift: (environmentId: number, applicationId: number) =>
    httpGet<DriftFinding[]>('/versioning/drift', { params: { environmentId, applicationId } }),
}
