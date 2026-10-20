/** 应用发布清单（`GenerationService.ReleaseSummary`）。 */
export interface ReleaseSummary {
  environmentId: number
  applicationId: number
  interfaceNos: string[]
  standardCodes: string[]
}
