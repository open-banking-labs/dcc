import type { ColumnDef } from '@tanstack/react-table'
import { Download, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PermissionGate } from '@/components/common/PermissionGate'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { FilterBar } from '@/components/data/FilterBar'
import { useToast } from '@/components/feedback/Toast'
import { Field, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { dash, formatDateTime } from '@/lib/format'
import { PERMISSION } from '@/lib/permissions'
import { standardStatusOptions } from '@/lib/status'
import type { StandardStatus, StandardView } from '@/types'

interface Filters {
  category: string
  status: string
}

export function StandardsListPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [filters, setFilters] = useState<Filters>({ category: '', status: '' })
  const [applied, setApplied] = useState<Filters>({ category: '', status: '' })
  const [deleteTarget, setDeleteTarget] = useState<StandardView | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [exporting, setExporting] = useState(false)

  const { data, loading, error, reload } = useAsync(
    () =>
      standardsApi.list({
        environmentId: environmentId ?? undefined,
        applicationId: applicationId ?? undefined,
        category: applied.category || undefined,
        status: (applied.status || undefined) as StandardStatus | undefined,
      }),
    [environmentId, applicationId, applied],
  )

  const rows = data ?? []

  const columns: ColumnDef<StandardView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.code}</span>,
    },
    {
      id: 'name',
      header: '名称',
      accessorFn: (row) => row.content.name,
    },
    {
      id: 'category',
      header: '分类',
      accessorFn: (row) => row.category ?? '',
      cell: ({ row }) => dash(row.original.category),
    },
    {
      id: 'dataType',
      header: '数据类型',
      accessorFn: (row) => row.content.dataType,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.content.dataType}</span>,
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
            <Link to={`/standards/${row.original.id}`}>查看</Link>
          </Button>
          <PermissionGate authority={PERMISSION.artifactWrite}>
            <Button asChild size="sm" variant="ghost">
              <Link to={`/standards/${row.original.id}/edit`}>编辑</Link>
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
      await standardsApi.remove(deleteTarget.id)
      toast.success('已删除', deleteTarget.code)
      setDeleteTarget(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  const handleExport = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setExporting(true)
    try {
      const items = await standardsApi.exportItems(environmentId, applicationId)
      const blob = new Blob([JSON.stringify(items, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'standards.json'
      anchor.click()
      URL.revokeObjectURL(url)
      toast.success('已导出', `共 ${items.length} 条`)
    } catch (err) {
      toast.error('导出失败', err instanceof Error ? err.message : undefined)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="数标管理"
        description="字段的统一口径：类型、长度与校验规则，可被表结构和接口字段引用。"
        actions={
          <>
            <Button size="sm" variant="outline" onClick={() => void handleExport()} disabled={exporting}>
              <Download className="h-4 w-4" />
              {exporting ? '导出中…' : '导出'}
            </Button>
            <PermissionGate authority={PERMISSION.artifactWrite}>
              <Button size="sm" variant="primary" onClick={() => navigate('/standards/new')}>
                <Plus className="h-4 w-4" />
                新建
              </Button>
            </PermissionGate>
          </>
        }
      />

      <FilterBar
        onSearch={() => setApplied(filters)}
        onReset={() => {
          const cleared = { category: '', status: '' }
          setFilters(cleared)
          setApplied(cleared)
        }}
      >
        <Field label="分类" htmlFor="filter-category" className="w-44">
          <TextField
            id="filter-category"
            value={filters.category}
            onChange={(value) => setFilters((prev) => ({ ...prev, category: value }))}
            placeholder="按分类筛选"
          />
        </Field>
        <Field label="状态" htmlFor="filter-status" className="w-40">
          <SelectField
            id="filter-status"
            value={filters.status}
            onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
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
        title="删除数标"
        description={deleteTarget ? `确认删除「${deleteTarget.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。` : undefined}
        loading={deleting}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
