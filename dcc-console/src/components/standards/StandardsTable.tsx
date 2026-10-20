import type { ColumnDef, OnChangeFn, PaginationState, RowSelectionState } from '@tanstack/react-table'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { dash, formatDateTime } from '@/lib/format'
import type { StandardSummaryView } from '@/types'

interface StandardsTableProps {
  rows: StandardSummaryView[]
  loading: boolean
  error: string | null
  onRetry: () => void
  /** 详情链接要带上的返回地址（当前列表 URL）。 */
  returnTo: string
  selectionMode: 'single' | 'multiple'
  rowSelection: RowSelectionState
  onRowSelectionChange: OnChangeFn<RowSelectionState>
  pagination: PaginationState
  onPaginationChange: OnChangeFn<PaginationState>
  pageCount: number
  total: number
}

/**
 * 查询页与维护页共用的数标表格：只展示轻量摘要，编码/名称链接到详情。
 * 两页的差别只在选择模式（多选 vs 单选）和工具栏。
 */
export function StandardsTable({
  rows,
  loading,
  error,
  onRetry,
  returnTo,
  selectionMode,
  rowSelection,
  onRowSelectionChange,
  pagination,
  onPaginationChange,
  pageCount,
  total,
}: StandardsTableProps) {
  const columns: ColumnDef<StandardSummaryView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      enableSorting: false,
      cell: ({ row }) => (
        <Link
          to={`/standards/${row.original.id}`}
          state={{ from: returnTo }}
          className="font-mono text-xs text-primary hover:underline"
        >
          {row.original.code}
        </Link>
      ),
    },
    {
      id: 'name',
      header: '名称',
      enableSorting: false,
      accessorFn: (row) => row.name ?? '',
      cell: ({ row }) => (
        <Link
          to={`/standards/${row.original.id}`}
          state={{ from: returnTo }}
          className="hover:underline"
        >
          {dash(row.original.name)}
        </Link>
      ),
    },
    {
      id: 'category',
      header: '分类',
      enableSorting: false,
      accessorFn: (row) => row.category ?? '',
      cell: ({ row }) => dash(row.original.category),
    },
    {
      id: 'dataType',
      header: '数据类型',
      enableSorting: false,
      accessorFn: (row) => row.dataType ?? '',
      cell: ({ row }) => <span className="font-mono text-xs">{dash(row.original.dataType)}</span>,
    },
    {
      id: 'status',
      header: '状态',
      enableSorting: false,
      accessorFn: (row) => row.status,
      cell: ({ row }) => <StatusBadge kind="standard" status={row.original.status} />,
    },
    {
      accessorKey: 'currentVersion',
      header: '版本',
      enableSorting: false,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.currentVersion}</span>,
    },
    {
      accessorKey: 'updatedAt',
      header: '更新时间',
      enableSorting: false,
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-muted-foreground">
          {formatDateTime(row.original.updatedAt)}
        </span>
      ),
    },
  ]

  return (
    <DataTable
      columns={columns}
      data={rows}
      loading={loading}
      error={error}
      onRetry={onRetry}
      emptyText="暂无数据"
      enableSelection
      selectionMode={selectionMode}
      rowSelection={rowSelection}
      onRowSelectionChange={onRowSelectionChange}
      getRowId={(row) => String(row.id)}
      enableSorting={false}
      manualPagination
      pagination={pagination}
      onPaginationChange={onPaginationChange}
      pageCount={pageCount}
      total={total}
    />
  )
}
