import type { FieldChange } from './common'

/** 版本升级建议（`VersioningService.BumpSuggestion`）；level 为 MAJOR/MINOR/PATCH/NONE。 */
export interface BumpSuggestion {
  level: string
  reasons: string[]
  changes: FieldChange[]
}

/** 漂移发现（`VersioningService.DriftFinding`）：快照重算哈希与记录不符。 */
export interface DriftFinding {
  artifactType: string
  code: string
  version: string
}
