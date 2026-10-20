import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { tablesApi } from '@/api/tables'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PermissionGate } from '@/components/common/PermissionGate'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { FilterBar } from '@/components/data/FilterBar'
import { useToast } from '@/components/feedback/Toast'
import { Field, SelectField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { formatDateTime } from '@/lib/format'
import { PERMISSION } from '@/lib/permissions'
import { standardStatusOptions } from '@/lib/status'
import type { StandardStatus, TableView } from '@/types'

export function TableListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [status, setStatus] = useState('')
  const [appliedStatus, setAppliedStatus] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<TableView | null>(null)
  const [deleting, setDeleting] = useState(false)

  const { data, loading, error, reload } = useAsync(
    () =>
      tablesApi.list({
        environmentId: environmentId ?? undefined,
        applicationId: applicationId ?? undefined,
        status: (appliedStatus || undefined) as StandardStatus | undefined,
      }),
    [environmentId, applicationId, appliedStatus],
  )
  const rows = data ?? []

  const columns: ColumnDef<TableView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.code}</span>,
    },
    { id: 'name', header: '名称', accessorFn: (row) => row.content.name },
    {
      id: 'columnCount',
      header: '列数',
      accessorFn: (row) => row.content.columns.length,
      cell: ({ row }) => row.original.content.columns.length,
    },
    {
      id: 'status',
      header: '状态',
      accessorFn: (row) => row.status,
      cell: ({ row }) => <StatusBadge kind="standard" status={row.original.status} />,
    },
    {
      accessorKey: 'currentVersion',
      header: '版本',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.currentVersion}</span>,
    },
    {
      accessorKey: 'updatedAt',
      header: '更新时间',
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-muted-foreground">
          {formatDateTime(row.original.updatedAt)}
        </span>
      ),
    },
    {
      id: 'actions',
      header: '操作',
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex items-center justify-end gap-1">
          <Button asChild size="sm" variant="ghost">
            <Link to={`/tables/${row.original.id}`}>查看</Link>
          </Button>
          <PermissionGate authority={PERMISSION.artifactWrite}>
            <Button asChild size="sm" variant="ghost">
              <Link to={`/tables/${row.original.id}/edit`}>编辑</Link>
            </Button>
          </PermissionGate>
          <PermissionGate authority={PERMISSION.artifactDelete}>
            <Button
              size="sm"
              variant="ghost"
              className="text-destructive hover:text-destructive"
              onClick={() => setDeleteTarget(row.original)}
            >
              删除
            </Button>
          </PermissionGate>
        </div>
      ),
    },
  ]

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await tablesApi.remove(deleteTarget.id)
      toast.success('已删除', deleteTarget.code)
      setDeleteTarget(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="表结构"
        description="当前环境/应用下的表结构；列可引用数标，需要时生成 DDL 与 Flyway 脚本。"
        actions={
          <PermissionGate authority={PERMISSION.artifactWrite}>
            <Button size="sm" variant="primary" onClick={() => navigate('/tables/new')}>
              <Plus className="h-4 w-4" />
              新建
            </Button>
          </PermissionGate>
        }
      />

      <FilterBar
        onSearch={() => setAppliedStatus(status)}
        onReset={() => {
          setStatus('')
          setAppliedStatus('')
        }}
      >
        <Field label="状态" htmlFor="filter-status" className="w-40">
          <SelectField
            id="filter-status"
            value={status}
            onChange={setStatus}
            options={standardStatusOptions()}
            placeholder="全部"
          />
        </Field>
      </FilterBar>

      <DataTable
        columns={columns}
        data={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="暂无数据"
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
        title="删除表结构"
        description={deleteTarget ? `确认删除「${deleteTarget.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。` : undefined}
        loading={deleting}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
