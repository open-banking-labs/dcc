import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { templatesApi } from '@/api/templates'
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
import type { StandardStatus, TemplateView } from '@/types'

export function TemplateListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [status, setStatus] = useState('')
  const [appliedStatus, setAppliedStatus] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<TemplateView | null>(null)
  const [deleting, setDeleting] = useState(false)

  const { data, loading, error, reload } = useAsync(
    () =>
      templatesApi.list({
        environmentId: environmentId ?? undefined,
        applicationId: applicationId ?? undefined,
        status: (appliedStatus || undefined) as StandardStatus | undefined,
      }),
    [environmentId, applicationId, appliedStatus],
  )
  const rows = data ?? []

  const columns: ColumnDef<TemplateView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.code}</span>,
    },
    { id: 'name', header: '名称', accessorFn: (row) => row.content.name },
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
            <Link to={`/templates/${row.original.id}`}>查看</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to={`/templates/${row.original.id}/edit`}>编辑</Link>
          </Button>
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
      await templatesApi.remove(deleteTarget.id)
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
        title="接口模板"
        description="接口模板：CRUD、版本、生命周期"
        actions={
          <Button size="sm" onClick={() => navigate('/templates/new')}>
            <Plus className="h-4 w-4" />
            新建
          </Button>
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
        title="删除接口模板"
        description={deleteTarget ? `确认删除「${deleteTarget.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。` : undefined}
        loading={deleting}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
