import { useState } from 'react'

import { versioningApi } from '@/api/versioning'
import { ErrorState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useResourceContext } from '@/hooks/useResourceContext'
import { ARTIFACT_TYPE_LABEL } from '@/lib/labels'
import type { DriftFinding } from '@/types'

export function DriftPage() {
  const toast = useToast()
  const { environmentId, applicationId, environments, applications } = useResourceContext()

  const [findings, setFindings] = useState<DriftFinding[] | null>(null)
  const [loading, setLoading] = useState(false)

  const envName = environments.find((env) => env.id === environmentId)?.name ?? '—'
  const appName = applications.find((app) => app.id === applicationId)?.name ?? '—'

  const check = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setLoading(true)
    try {
      setFindings(await versioningApi.drift(environmentId, applicationId))
    } catch (err) {
      toast.error('检查失败', err instanceof Error ? err.message : undefined)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="漂移检查"
        description="检查范围内快照重算哈希与记录是否仍一致"
        actions={
          <Button size="sm" onClick={() => void check()} disabled={loading}>
            {loading ? '检查中…' : '开始检查'}
          </Button>
        }
      />

      <p className="text-sm text-muted-foreground">
        范围：环境 <span className="text-foreground">{envName}</span> · 应用{' '}
        <span className="text-foreground">{appName}</span>
      </p>

      {findings === null ? (
        <div className="rounded-md border border-border bg-card">
          <p className="px-3 py-12 text-center text-sm text-muted-foreground">
            点击"开始检查"运行漂移检测
          </p>
        </div>
      ) : findings.length === 0 ? (
        <div className="rounded-md border border-border bg-card">
          <p className="px-3 py-12 text-center text-sm text-muted-foreground">暂无数据</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
          <table className="w-full border-collapse text-table">
            <thead>
              <tr className="border-b border-border bg-secondary text-left text-[12px] font-semibold text-muted-foreground">
                <th className="px-3 py-2 font-medium">产物类型</th>
                <th className="px-3 py-2 font-medium">编码</th>
                <th className="px-3 py-2 font-medium">版本</th>
              </tr>
            </thead>
            <tbody>
              {findings.map((finding, index) => (
                <tr key={`${finding.artifactType}-${finding.code}-${finding.version}-${index}`} className="border-b border-border last:border-0">
                  <td className="px-3 py-2">
                    {ARTIFACT_TYPE_LABEL[finding.artifactType as keyof typeof ARTIFACT_TYPE_LABEL] ??
                      finding.artifactType}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{finding.code}</td>
                  <td className="px-3 py-2 font-mono text-xs">{finding.version}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {findings !== null && findings.length > 0 && (
        <ErrorState message={`发现 ${findings.length} 处漂移，快照内容与记录哈希不符。`} />
      )}
    </div>
  )
}
