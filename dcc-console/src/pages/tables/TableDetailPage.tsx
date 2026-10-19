import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { tablesApi } from '@/api/tables'
import { CodeBlock } from '@/components/common/CodeBlock'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { KeyValueList } from '@/components/common/KeyValueList'
import { PermissionGate, useHasAuthority } from '@/components/common/PermissionGate'
import { StatusBadge } from '@/components/common/StatusBadge'
import { TableChangeSetView } from '@/components/data/DiffView'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { Field, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAsync } from '@/hooks/useAsync'
import { dash, formatDateTime, yesNo } from '@/lib/format'
import { CHANGE_TYPE_LABEL, STANDARD_STATUS_LABEL } from '@/lib/labels'
import { PERMISSION } from '@/lib/permissions'
import { STANDARD_TRANSITIONS } from '@/lib/status'
import type { TableChangeSet, TableDdl } from '@/types'

export function TableDetailPage() {
  const { id } = useParams()
  const numericId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()

  const { data: table, loading, error, reload } = useAsync(() => tablesApi.get(numericId), [numericId])
  const { data: history } = useAsync(() => tablesApi.history(numericId), [numericId])
  const canTransition = useHasAuthority(PERMISSION.artifactTransition)

  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const [diffFrom, setDiffFrom] = useState('')
  const [diffTo, setDiffTo] = useState('')
  const [diffChanges, setDiffChanges] = useState<TableChangeSet | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)

  const [schema, setSchema] = useState('')
  const [createDdl, setCreateDdl] = useState<TableDdl | null>(null)
  const [alterDdl, setAlterDdl] = useState<TableDdl | null>(null)
  const [ddlLoading, setDdlLoading] = useState(false)

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
      const result = await tablesApi.diff(numericId, diffFrom, diffTo)
      setDiffChanges(result.changes)
    } catch (err) {
      toast.error('对比失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDiffLoading(false)
    }
  }

  const doVerify = async (version: string) => {
    try {
      const ok = await tablesApi.verify(numericId, version)
      if (ok) toast.success('校验通过', version)
      else toast.error('校验失败：内容哈希不匹配', version)
    } catch (err) {
      toast.error('校验失败', err instanceof Error ? err.message : undefined)
    }
  }

  const genCreateDdl = async () => {
    setDdlLoading(true)
    try {
      setCreateDdl(await tablesApi.ddl(numericId, schema.trim() || undefined))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDdlLoading(false)
    }
  }

  const genAlterDdl = async () => {
    if (!diffFrom || !diffTo) {
      toast.error('请选择两个版本')
      return
    }
    setDdlLoading(true)
    try {
      setAlterDdl(await tablesApi.alterDdl(numericId, diffFrom, diffTo, schema.trim() || undefined))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDdlLoading(false)
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
  if (!table) {
    return <EmptyState />
  }

  const versions = history ?? []
  const transitions = STANDARD_TRANSITIONS[table.status]
  const versionOptions = versions.map((version) => ({ value: version.version, label: version.version }))
  const columns = table.content.columns ?? []

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title={table.code}
        description={table.content.name}
        actions={
          <>
            <PermissionGate authority={PERMISSION.artifactWrite}>
              <Button asChild size="sm" variant="outline">
                <Link to={`/tables/${table.id}/edit`}>编辑</Link>
              </Button>
            </PermissionGate>
            {canTransition &&
              transitions.map((target) => (
                <Button
                  key={target}
                  size="sm"
                  disabled={busy}
                  onClick={() => void run(() => tablesApi.transition(numericId, target), '状态已更新')}
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
          <TabsTrigger value="ddl">DDL</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="space-y-4">
            <div className="rounded-md border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-medium text-foreground">基本信息</h3>
              <KeyValueList
                items={[
                  { label: '状态', value: <StatusBadge kind="standard" status={table.status} /> },
                  { label: '当前版本', value: <span className="font-mono text-xs">{table.currentVersion}</span> },
                  { label: '表名', value: dash(table.content.name) },
                  { label: '分片键', value: dash(table.content.shardKey) },
                  { label: '环境 / 应用', value: `${table.environmentId} / ${table.applicationId}` },
                  { label: '更新时间', value: formatDateTime(table.updatedAt) },
                  { label: '描述', value: dash(table.content.description) },
                ]}
              />
            </div>

            <div className="overflow-hidden rounded-md border border-border bg-card">
              <table className="w-full border-collapse text-table">
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th className="px-3 py-2 font-medium">列名</th>
                    <th className="px-3 py-2 font-medium">数标</th>
                    <th className="px-3 py-2 font-medium">类型</th>
                    <th className="px-3 py-2 font-medium">长度</th>
                    <th className="px-3 py-2 font-medium">精度</th>
                    <th className="px-3 py-2 font-medium">可空</th>
                    <th className="px-3 py-2 font-medium">主键</th>
                    <th className="px-3 py-2 font-medium">默认值</th>
                    <th className="px-3 py-2 font-medium">注释</th>
                  </tr>
                </thead>
                <tbody>
                  {columns.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="px-3 py-12 text-center text-sm text-muted-foreground">
                        暂无数据
                      </td>
                    </tr>
                  ) : (
                    columns.map((column) => (
                      <tr key={column.columnName} className="border-b border-border last:border-0">
                        <td className="px-3 py-2 font-mono text-xs">{column.columnName}</td>
                        <td className="px-3 py-2 text-muted-foreground">{dash(column.standardCode)}</td>
                        <td className="px-3 py-2 font-mono text-xs">{dash(column.dataType)}</td>
                        <td className="px-3 py-2">{dash(column.length)}</td>
                        <td className="px-3 py-2">{dash(column.scale)}</td>
                        <td className="px-3 py-2">{yesNo(column.nullable)}</td>
                        <td className="px-3 py-2">{yesNo(column.primaryKey)}</td>
                        <td className="px-3 py-2 text-muted-foreground">{dash(column.defaultValue)}</td>
                        <td className="px-3 py-2 text-muted-foreground">{dash(column.comment)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
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
                          {version.version !== table.currentVersion && (
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() =>
                                void run(
                                  () => tablesApi.rollback(numericId, version.version),
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
            {diffChanges && <TableChangeSetView changes={diffChanges} />}
          </div>
        </TabsContent>

        <TabsContent value="ddl">
          <div className="space-y-4">
            <div className="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-3">
              <Field label="Schema" htmlFor="ddl-schema" className="w-48">
                <TextField
                  id="ddl-schema"
                  value={schema}
                  onChange={setSchema}
                  placeholder="可选，如 public"
                />
              </Field>
              <Button size="sm" onClick={() => void genCreateDdl()} disabled={ddlLoading}>
                {ddlLoading ? '生成中…' : '生成建表 DDL'}
              </Button>
            </div>

            {createDdl && (
              <div className="space-y-3">
                <div className="rounded-md border border-border bg-card p-3 text-sm">
                  <span className="text-muted-foreground">方言：</span>
                  <span className="font-mono text-xs">{createDdl.dialect}</span>
                  <span className="ml-4 text-muted-foreground">Flyway：</span>
                  <span className="font-mono text-xs">{createDdl.flywayFileName}</span>
                </div>
                <div className="space-y-1">
                  <h4 className="text-sm font-medium text-foreground">DDL</h4>
                  <CodeBlock code={createDdl.ddl} language="sql" />
                </div>
                <div className="space-y-1">
                  <h4 className="text-sm font-medium text-foreground">Flyway 脚本</h4>
                  <CodeBlock code={createDdl.flywayScript} language="sql" />
                </div>
              </div>
            )}

            <div className="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-3">
              <div className="w-40">
                <SelectField value={diffFrom} onChange={setDiffFrom} options={versionOptions} placeholder="起始版本" />
              </div>
              <div className="w-40">
                <SelectField value={diffTo} onChange={setDiffTo} options={versionOptions} placeholder="目标版本" />
              </div>
              <Button size="sm" variant="outline" onClick={() => void genAlterDdl()} disabled={ddlLoading}>
                {ddlLoading ? '生成中…' : '生成变更 DDL'}
              </Button>
            </div>

            {alterDdl && (
              <div className="space-y-3">
                <div className="rounded-md border border-border bg-card p-3 text-sm">
                  <span className="text-muted-foreground">Flyway：</span>
                  <span className="font-mono text-xs">{alterDdl.flywayFileName}</span>
                </div>
                <CodeBlock code={alterDdl.ddl} language="sql" />
                <CodeBlock code={alterDdl.flywayScript} language="sql" />
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="删除表结构"
        description={`确认删除「${table.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。`}
        loading={busy}
        onConfirm={() =>
          void run(async () => {
            await tablesApi.remove(numericId)
            navigate('/tables')
          }, '已删除')
        }
      />
    </div>
  )
}
