import type { ColumnDef } from '@tanstack/react-table'
import { useMemo, useState } from 'react'

import { auditApi, canRestore, restoreAuditEntity, type AuditPage } from '@/api/audit'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { useHasAuthority } from '@/components/common/PermissionGate'
import { CodeBlock } from '@/components/common/CodeBlock'
import { DataTable } from '@/components/data/DataTable'
import { useToast } from '@/components/feedback/Toast'
import { SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useAsync } from '@/hooks/useAsync'
import { dash, formatDateTime } from '@/lib/format'
import { AUDIT_ACTION_LABEL, AUDIT_ENTITY_LABEL } from '@/lib/labels'
import { AUTHORITY } from '@/lib/permissions'
import type { AuditAction, AuditEntryView } from '@/types/audit'

const PAGE_SIZE = 20

const ACTION_OPTIONS = Object.entries(AUDIT_ACTION_LABEL).map(([value, label]) => ({ value, label }))

const ENTITY_TYPE_OPTIONS = Object.entries(AUDIT_ENTITY_LABEL).map(([value, label]) => ({ value, label }))

interface Filters {
  entityType: string
  entityId: string
  actor: string
  action: string
  from: string
  to: string
}

const EMPTY_FILTERS: Filters = { entityType: '', entityId: '', actor: '', action: '', from: '', to: '' }

const ACTION_CLASS: Record<AuditAction, string> = {
  CREATE: 'font-medium text-green-700',
  UPDATE: 'font-medium text-blue-700',
  LOGICAL_DELETE: 'font-medium text-destructive',
  RESTORE: 'font-medium text-green-700',
}

/** datetime-local 值 → ISO Instant；空值/非法值返回 null。 */
function toIso(value: string): string | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

/** 紧凑 JSON 文本 → 美化缩进；解析失败则原样返回。 */
function prettyJson(value: string | null): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

/** 链路弹窗：该对象的完整历史（旧→新）、哈希链校验与（尾行删除时）恢复。 */
function HistoryDialog({
  target,
  onClose,
  onRestored,
}: {
  target: { entityType: string; entityId: number } | null
  onClose: () => void
  onRestored: () => void
}) {
  const toast = useToast()
  const isAdmin = useHasAuthority(AUTHORITY.admin)
  const { data, loading, error, reload } = useAsync(
    () => (target ? auditApi.history(target.entityType, target.entityId) : Promise.resolve([])),
    [target?.entityType, target?.entityId],
  )
  const verify = useAsync(
    () => (target ? auditApi.verify(target.entityType, target.entityId) : Promise.resolve(null)),
    [target?.entityType, target?.entityId],
  )

  const entries = data ?? []
  const latest = entries.length > 0 ? entries[entries.length - 1] : null
  const deletedNow = latest?.action === 'LOGICAL_DELETE'
  const [restoring, setRestoring] = useState(false)

  const doRestore = async () => {
    if (!latest) return
    setRestoring(true)
    try {
      await restoreAuditEntity(latest.entityType, latest.entityId, latest.entityCode)
      toast.success('已恢复', latest.entityCode ? `${latest.entityCode} (#${latest.entityId})` : `#${latest.entityId}`)
      reload()
      verify.reload()
      onRestored()
      onClose()
    } catch (err) {
      toast.error('恢复失败', err instanceof Error ? err.message : undefined)
    } finally {
      setRestoring(false)
    }
  }

  return (
    <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>
            审计链路 · {AUDIT_ENTITY_LABEL[target?.entityType ?? ''] ?? target?.entityType} #{target?.entityId}
          </DialogTitle>
          <DialogDescription>
            每条记录与其描述的变更同事务提交；哈希链把前后镜像串起来，改动即露痕。
          </DialogDescription>
        </DialogHeader>

        {verify.data && (
          <div
            className={
              verify.data.intact
                ? 'rounded-md border border-border bg-muted/40 px-3 py-2 text-sm'
                : 'rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive'
            }
          >
            {verify.data.intact ? '哈希链校验：完好' : `哈希链校验：发现 ${verify.data.problems.length} 处异常`}
            {!verify.data.intact &&
              verify.data.problems.map((problem) => (
                <div key={problem} className="mt-1 font-mono text-xs">
                  {problem}
                </div>
              ))}
          </div>
        )}

        {loading && <p className="py-4 text-sm text-muted-foreground">加载中…</p>}
        {error && <p className="py-4 text-sm text-destructive">{error}</p>}

          {!loading && !error && (
            <div className="max-h-96 space-y-2 overflow-y-auto pr-1">
              {entries.map((entry, index) => (
                <div key={entry.id} className="rounded-md border border-border p-2 text-sm">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="text-xs text-muted-foreground">
                      #{index + 1}
                    </span>
                  <span className="font-medium text-foreground">
                    {AUDIT_ACTION_LABEL[entry.action] ?? entry.action}
                  </span>
                  <span className="text-muted-foreground">{formatDateTime(entry.occurredAt)}</span>
                  <span className="text-muted-foreground">执行者：{entry.actor}</span>
                  {entry.reason && <span className="text-muted-foreground">原因：{entry.reason}</span>}
                </div>
                {entry.changedFields && entry.changedFields !== 'null' && (
                  <p className="mt-1 text-xs text-muted-foreground">变更字段：{entry.changedFields}</p>
                )}
              </div>
            ))}
            {entries.length === 0 && <p className="py-3 text-sm text-muted-foreground">暂无记录</p>}
          </div>
        )}

        <div className="mt-2 flex justify-end gap-2">
          {isAdmin && deletedNow && (
            <Button size="sm" variant="outline" disabled={restoring} onClick={() => void doRestore()}>
              {restoring ? '处理中…' : '恢复该对象'}
            </Button>
          )}
          <Button size="sm" variant="outline" onClick={onClose}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** 详情弹窗：单条审计记录的完整前后镜像。 */
function DetailDialog({ entry, onClose }: { entry: AuditEntryView | null; onClose: () => void }) {
  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>
            审计详情 · {AUDIT_ACTION_LABEL[entry?.action ?? 'CREATE'] ?? entry?.action} ·{' '}
            {showEntityTitle(entry)}
          </DialogTitle>
          <DialogDescription>
            {formatDateTime(entry?.occurredAt)} · 执行者 {entry?.actor ?? '—'} · 原因 {dash(entry?.reason)}
          </DialogDescription>
        </DialogHeader>
        {entry && (
          <div className="space-y-2 text-sm">
            <p className="text-xs text-muted-foreground">
              链值 hash: <span className="font-mono">{entry.hash}</span>
            </p>
            {entry.beforeState && (
              <CodeBlock code={prettyJson(entry.beforeState)} language="before_state" />
            )}
            {entry.afterState && (
              <CodeBlock code={prettyJson(entry.afterState)} language="after_state" />
            )}
            {!entry.beforeState && !entry.afterState && (
              <p className="text-xs text-muted-foreground">该记录没有前后镜像</p>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function showEntityTitle(entry: AuditEntryView | null): string {
  if (!entry) return ''
  const label = AUDIT_ENTITY_LABEL[entry.entityType] ?? entry.entityType
  return `#${entry.entityId} ${label}${entry.entityCode ? `（${entry.entityCode}）` : ''}`
}

/** 有恢复端点的对象类型（租户按 code 恢复，其余按 id）。 */
function isRestorable(entry: AuditEntryView): boolean {
  return canRestore(entry.entityType)
}

/** 审计日志（管理员）：谁在何时对哪行做了什么；删除过的行可在此恢复。 */
export function AuditPage() {
  const toast = useToast()
  const isAdmin = useHasAuthority(AUTHORITY.admin)

  const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS)
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)

  const query = useMemo(
    () => ({
      entityType: applied.entityType || null,
      entityId: applied.entityId ? Number(applied.entityId) : null,
      actor: applied.actor || null,
      action: (applied.action || null) as AuditAction | null,
      from: toIso(applied.from),
      to: toIso(applied.to),
      page,
      size: PAGE_SIZE,
    }),
    [applied, page],
  )

  const { data, loading, error, reload } = useAsync(() => auditApi.search(query), [query])
  const pageData = data as AuditPage | null
  const rows = pageData?.content ?? []

  const [detail, setDetail] = useState<AuditEntryView | null>(null)
  const [historyTarget, setHistoryTarget] = useState<{ entityType: string; entityId: number } | null>(null)
  const [restoreTarget, setRestoreTarget] = useState<AuditEntryView | null>(null)
  const [restoring, setRestoring] = useState(false)

  const apply = () => {
    setPage(0)
    setApplied(draft)
  }
  const reset = () => {
    setDraft(EMPTY_FILTERS)
    setApplied(EMPTY_FILTERS)
    setPage(0)
  }

  const doRestore = async () => {
    if (!restoreTarget) return
    setRestoring(true)
    try {
      await restoreAuditEntity(restoreTarget.entityType, restoreTarget.entityId, restoreTarget.entityCode)
      toast.success('已恢复', restoreTarget.entityCode ?? `#${restoreTarget.entityId}`)
      setRestoreTarget(null)
      reload()
    } catch (err) {
      toast.error('恢复失败', err instanceof Error ? err.message : undefined)
    } finally {
      setRestoring(false)
    }
  }

  const columns: ColumnDef<AuditEntryView, any>[] = [
    {
      accessorKey: 'occurredAt',
      header: '时间',
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-muted-foreground">{formatDateTime(row.original.occurredAt)}</span>
      ),
    },
    {
      id: 'entityType',
      header: '对象类型',
      accessorFn: (row) => row.entityType,
      cell: ({ row }) => AUDIT_ENTITY_LABEL[row.original.entityType] ?? row.original.entityType,
    },
    {
      id: 'object',
      header: '对象',
      accessorFn: (row) => row.entityCode ?? String(row.entityId),
      cell: ({ row }) => (
        <span className="font-mono text-xs">
          {row.original.entityCode
            ? `${row.original.entityCode} (#${row.original.entityId})`
            : `#${row.original.entityId}`}
        </span>
      ),
    },
    {
      id: 'action',
      header: '操作',
      accessorFn: (row) => row.action,
      cell: ({ row }) => (
        <span className={ACTION_CLASS[row.original.action]}>
          {AUDIT_ACTION_LABEL[row.original.action] ?? row.original.action}
        </span>
      ),
    },
    { id: 'actor', header: '执行者', accessorFn: (row) => row.actor },
    {
      id: 'reason',
      header: '原因',
      accessorFn: (row) => row.reason ?? '',
      cell: ({ row }) => dash(row.original.reason),
    },
    {
      id: 'actions',
      header: '操作',
      enableSorting: false,
      cell: ({ row }) => {
        const entry = row.original
        return (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setDetail(entry)}>
              详情
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setHistoryTarget({ entityType: entry.entityType, entityId: entry.entityId })}
            >
              链路
            </Button>
            {isAdmin && isRestorable(entry) && (
              <Button
                size="sm"
                variant="ghost"
                className="text-destructive hover:text-destructive"
                onClick={() => setRestoreTarget(entry)}
              >
                恢复
              </Button>
            )}
          </div>
        )
      },
    },
  ]

  const totalPages = Math.max(1, pageData?.totalPages ?? 1)

  return (
    <div className="space-y-4 p-6">
      <PageHeader title="审计日志" description="追加写入的操作历史：谁在何时对哪行做了什么（管理员）" />

      <div className="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-3">
        <div className="w-36">
          <label className="text-xs text-muted-foreground">对象类型</label>
          <SelectField
            value={draft.entityType}
            onChange={(value) => setDraft({ ...draft, entityType: value })}
            options={ENTITY_TYPE_OPTIONS}
            placeholder="全部"
          />
        </div>
        <div className="w-24">
          <label className="text-xs text-muted-foreground">对象 id</label>
          <TextField value={draft.entityId} onChange={(value) => setDraft({ ...draft, entityId: value })} placeholder="如 3" />
        </div>
        <div className="w-32">
          <label className="text-xs text-muted-foreground">执行者</label>
          <TextField value={draft.actor} onChange={(value) => setDraft({ ...draft, actor: value })} placeholder="精确匹配" />
        </div>
        <div className="w-32">
          <label className="text-xs text-muted-foreground">操作</label>
          <SelectField
            value={draft.action}
            onChange={(value) => setDraft({ ...draft, action: value })}
            options={ACTION_OPTIONS}
            placeholder="全部"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">时间起</label>
          <input
            type="datetime-local"
            value={draft.from}
            onChange={(event) => setDraft({ ...draft, from: event.target.value })}
            className="flex h-8 rounded-md border border-input bg-background px-2 text-sm"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">时间止</label>
          <input
            type="datetime-local"
            value={draft.to}
            onChange={(event) => setDraft({ ...draft, to: event.target.value })}
            className="flex h-8 rounded-md border border-input bg-background px-2 text-sm"
          />
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button size="sm" onClick={apply}>
            查询
          </Button>
          <Button size="sm" variant="outline" onClick={reset}>
            重置
          </Button>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        pageSize={PAGE_SIZE}
        emptyText="暂无审计记录"
      />

      <div className="flex items-center justify-end gap-3 text-sm text-muted-foreground">
        <span>共 {pageData?.totalElements ?? 0} 条 · 第 {page + 1}/{totalPages} 页</span>
        <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          上一页
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={page + 1 >= totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          下一页
        </Button>
      </div>

      <DetailDialog entry={detail} onClose={() => setDetail(null)} />
      <HistoryDialog target={historyTarget} onClose={() => setHistoryTarget(null)} onRestored={reload} />

      <ConfirmDialog
        open={restoreTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRestoreTarget(null)
        }}
        title="恢复对象"
        description={
          restoreTarget
            ? `确认恢复 ${AUDIT_ENTITY_LABEL[restoreTarget.entityType] ?? restoreTarget.entityType} #${restoreTarget.entityId}${
                restoreTarget.entityCode ? `（${restoreTarget.entityCode}）` : ''
              }？若已有新对象占用了同一编码，恢复将失败。`
            : undefined
        }
        confirmText="恢复"
        loading={restoring}
        onConfirm={() => void doRestore()}
      />
    </div>
  )
}
