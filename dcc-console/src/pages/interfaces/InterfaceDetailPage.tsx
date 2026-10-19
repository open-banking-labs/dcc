import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { interfacesApi } from '@/api/interfaces'
import { CodeBlock } from '@/components/common/CodeBlock'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { KeyValueList } from '@/components/common/KeyValueList'
import { PermissionGate, useHasAuthority } from '@/components/common/PermissionGate'
import { StatusBadge } from '@/components/common/StatusBadge'
import { FieldChangeList } from '@/components/data/DiffView'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { SelectField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAsync } from '@/hooks/useAsync'
import { dash, formatDateTime } from '@/lib/format'
import { CHANGE_TYPE_LABEL, STANDARD_STATUS_LABEL } from '@/lib/labels'
import { PERMISSION } from '@/lib/permissions'
import { STANDARD_TRANSITIONS } from '@/lib/status'
import type { FieldChange } from '@/types'

export function InterfaceDetailPage() {
  const { id } = useParams()
  const numericId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()

  const { data: iface, loading, error, reload } = useAsync(
    () => interfacesApi.get(numericId),
    [numericId],
  )
  const { data: history } = useAsync(() => interfacesApi.history(numericId), [numericId])
  const canTransition = useHasAuthority(PERMISSION.artifactTransition)

  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [diffFrom, setDiffFrom] = useState('')
  const [diffTo, setDiffTo] = useState('')
  const [diffChanges, setDiffChanges] = useState<FieldChange[] | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)

  const run = async (action: () => Promise<unknown>, okTitle: string) => {
    setBusy(true)
    try {
      await action()
      toast.success(okTitle)
      reload()
    } catch (err) {
      toast.error('操作失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const doDiff = async () => {
    if (!diffFrom || !diffTo) {
      toast.error('请选择两个版本')
      return
    }
    setDiffLoading(true)
    try {
      const result = await interfacesApi.diff(numericId, diffFrom, diffTo)
      setDiffChanges(result.changes)
    } catch (err) {
      toast.error('对比失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDiffLoading(false)
    }
  }

  const doVerify = async (version: string) => {
    try {
      const ok = await interfacesApi.verify(numericId, version)
      if (ok) toast.success('校验通过', version)
      else toast.error('校验失败：内容哈希不匹配', version)
    } catch (err) {
      toast.error('校验失败', err instanceof Error ? err.message : undefined)
    }
  }

  if (loading) {
    return (
      <div className="p-6">
        <LoadingState />
      </div>
    )
  }
  if (error) {
    return (
      <div className="rounded-md border border-border bg-card">
        <ErrorState message={error} onRetry={reload} />
      </div>
    )
  }
  if (!iface) {
    return <EmptyState />
  }

  const versions = history ?? []
  const transitions = STANDARD_TRANSITIONS[iface.status]
  const versionOptions = versions.map((version) => ({ value: version.version, label: version.version }))

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title={iface.interfaceNo}
        description={iface.content.name}
        actions={
          <>
            <PermissionGate authority={PERMISSION.artifactWrite}>
              <Button asChild size="sm" variant="outline">
                <Link to={`/interfaces/${iface.id}/edit`}>编辑</Link>
              </Button>
            </PermissionGate>
            {canTransition &&
              transitions.map((target) => (
                <Button
                  key={target}
                  size="sm"
                  disabled={busy}
                  onClick={() => void run(() => interfacesApi.transition(numericId, target), '状态已更新')}
                >
                  转为 {STANDARD_STATUS_LABEL[target]}
                </Button>
              ))}
            <PermissionGate authority={PERMISSION.artifactDelete}>
              <Button
                size="sm"
                variant="outline"
                className="text-destructive hover:text-destructive"
                onClick={() => setConfirmDelete(true)}
              >
                删除
              </Button>
            </PermissionGate>
          </>
        }
      />

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="history">版本历史</TabsTrigger>
          <TabsTrigger value="diff">差异</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="space-y-4">
            <div className="rounded-md border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-medium text-foreground">基本信息</h3>
              <KeyValueList
                items={[
                  { label: '状态', value: <StatusBadge kind="standard" status={iface.status} /> },
                  { label: '当前版本', value: <span className="font-mono text-xs">{iface.currentVersion}</span> },
                  { label: '模板', value: dash(iface.templateId) },
                  { label: '业务模块', value: dash(iface.content.businessModule) },
                  { label: 'URL', value: dash(iface.content.url) },
                  { label: '短名', value: dash(iface.content.shortName) },
                  { label: '环境 / 应用', value: `${iface.environmentId} / ${iface.applicationId}` },
                  { label: '更新时间', value: formatDateTime(iface.updatedAt) },
                  { label: '描述', value: dash(iface.content.description) },
                ]}
              />
            </div>
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-foreground">输入字段</h3>
              <CodeBlock code={JSON.stringify(iface.content.input, null, 2)} language="json" />
            </div>
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-foreground">输出字段</h3>
              <CodeBlock code={JSON.stringify(iface.content.output, null, 2)} language="json" />
            </div>
          </div>
        </TabsContent>

        <TabsContent value="history">
          <div className="overflow-hidden rounded-md border border-border bg-card">
            <table className="w-full border-collapse text-table">
              <thead>
                <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                  <th className="px-3 py-2 font-medium">版本</th>
                  <th className="px-3 py-2 font-medium">级别</th>
                  <th className="px-3 py-2 font-medium">说明</th>
                  <th className="px-3 py-2 font-medium">作者</th>
                  <th className="px-3 py-2 font-medium">时间</th>
                  <th className="px-3 py-2 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {versions.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-12 text-center text-sm text-muted-foreground">
                      暂无数据
                    </td>
                  </tr>
                ) : (
                  versions.map((version) => (
                    <tr key={version.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2 font-mono text-xs">{version.version}</td>
                      <td className="px-3 py-2">{CHANGE_TYPE_LABEL[version.changeType]}</td>
                      <td className="px-3 py-2 text-muted-foreground">{dash(version.message)}</td>
                      <td className="px-3 py-2 text-muted-foreground">{dash(version.author)}</td>
                      <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                        {formatDateTime(version.createdAt)}
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex justify-end gap-1">
                          <Button size="sm" variant="ghost" onClick={() => void doVerify(version.version)}>
                            校验
                          </Button>
                          {version.version !== iface.currentVersion && (
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() =>
                                void run(
                                  () => interfacesApi.rollback(numericId, version.version),
                                  `已回滚到 ${version.version}`,
                                )
                              }
                            >
                              回滚
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </TabsContent>

        <TabsContent value="diff">
          <div className="space-y-3">
            <div className="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-3">
              <div className="w-40">
                <SelectField value={diffFrom} onChange={setDiffFrom} options={versionOptions} placeholder="起始版本" />
              </div>
              <div className="w-40">
                <SelectField value={diffTo} onChange={setDiffTo} options={versionOptions} placeholder="目标版本" />
              </div>
              <Button size="sm" onClick={() => void doDiff()} disabled={diffLoading}>
                {diffLoading ? '对比中…' : '对比'}
              </Button>
            </div>
            {diffChanges && <FieldChangeList changes={diffChanges} />}
          </div>
        </TabsContent>
      </Tabs>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="删除接口"
        description={`确认删除「${iface.interfaceNo}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。`}
        loading={busy}
        onConfirm={() =>
          void run(async () => {
            await interfacesApi.remove(numericId)
            navigate('/interfaces')
          }, '已删除')
        }
      />
    </div>
  )
}
