import { useState } from 'react'

import { generationApi } from '@/api/generation'
import { CodeBlock } from '@/components/common/CodeBlock'
import { EmptyState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { Field, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { ReleaseSummary } from '@/types'

export function GenerationPage() {
  const toast = useToast()
  const { environmentId, applicationId, environments, applications } = useResourceContext()

  const [openApi, setOpenApi] = useState<string | null>(null)
  const [openApiLoading, setOpenApiLoading] = useState(false)

  const [version, setVersion] = useState('')
  const [releasing, setReleasing] = useState(false)

  const envName = environments.find((env) => env.id === environmentId)?.name ?? `#${environmentId}`
  const appName = applications.find((app) => app.id === applicationId)?.name ?? `#${applicationId}`

  const {
    data: summary,
    loading: manifestLoading,
    reload: loadManifest,
  } = useAsync<ReleaseSummary | null>(
    () =>
      environmentId == null || applicationId == null
        ? Promise.resolve(null)
        : generationApi.manifest(environmentId, applicationId),
    [environmentId, applicationId],
  )

  const genOpenApi = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setOpenApiLoading(true)
    try {
      setOpenApi(await generationApi.openApi(environmentId, applicationId))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setOpenApiLoading(false)
    }
  }

  const release = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setReleasing(true)
    try {
      const blob = await generationApi.release(
        environmentId,
        applicationId,
        version.trim() || undefined,
      )
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'dcc-bundle.jar'
      anchor.click()
      URL.revokeObjectURL(url)
      toast.success('已发布', `${appName} · ${envName}`)
    } catch (err) {
      toast.error('发布失败', err instanceof Error ? err.message : undefined)
    } finally {
      setReleasing(false)
    }
  }

  const noScope = environmentId == null || applicationId == null

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="应用发布"
        description="以应用为单位发布：打包全部接口 DTO + 数标校验类 + OpenAPI"
      />

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-foreground">发布清单</h3>
          <Button size="sm" variant="outline" onClick={() => void loadManifest()} disabled={manifestLoading}>
            {manifestLoading ? '加载中…' : '刷新'}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          当前发布范围：{appName} · {envName}（使用顶栏选择的环境与应用）
        </p>
        {noScope ? (
          <EmptyState text="请先在顶栏选择环境与应用" />
        ) : summary ? (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="mb-1 text-xs font-medium text-muted-foreground">
                接口（{summary.interfaceNos.length}）
              </div>
              {summary.interfaceNos.length === 0 ? (
                <p className="text-sm text-muted-foreground">无</p>
              ) : (
                <ul className="space-y-0.5">
                  {summary.interfaceNos.map((no) => (
                    <li key={no} className="font-mono text-xs text-foreground">
                      {no}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <div className="mb-1 text-xs font-medium text-muted-foreground">
                数标（{summary.standardCodes.length}）
              </div>
              {summary.standardCodes.length === 0 ? (
                <p className="text-sm text-muted-foreground">无</p>
              ) : (
                <ul className="space-y-0.5">
                  {summary.standardCodes.map((code) => (
                    <li key={code} className="font-mono text-xs text-foreground">
                      {code}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        ) : (
          <EmptyState text="暂无数据" />
        )}
      </section>

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">OpenAPI 文档</h3>
        <Button size="sm" onClick={() => void genOpenApi()} disabled={openApiLoading || noScope}>
          {openApiLoading ? '生成中…' : '生成'}
        </Button>
        {openApi && <CodeBlock code={openApi} language="json" />}
      </section>

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">发布（打包 JAR）</h3>
        <p className="text-xs text-muted-foreground">
          一次发布打包该应用全部接口与数标，不提供单接口/单数标导出。
        </p>
        <div className="flex items-end gap-3">
          <Field label="版本（可选）" htmlFor="rel-version" className="w-40">
            <TextField id="rel-version" value={version} onChange={setVersion} placeholder="留空取当前" />
          </Field>
          <Button size="sm" onClick={() => void release()} disabled={releasing || noScope}>
            {releasing ? '发布中…' : '发布'}
          </Button>
        </div>
      </section>
    </div>
  )
}
