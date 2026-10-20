import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { ArrowDown, ArrowUp, ChevronsUpDown, Inbox, TriangleAlert } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

export interface DataTableProps<T> {
  columns: ColumnDef<T, any>[]
  data: T[]
  loading?: boolean
  error?: string | null
  onRetry?: () => void
  pageSize?: number
  emptyText?: string
}

const PAGE_SIZES = [10, 20, 50, 100]

/** 通用表格：GitHub Box 容器 + 排序 + 前端分页 + 三态（loading/empty/error）。 */
export function DataTable<T>({
  columns,
  data,
  loading = false,
  error = null,
  onRetry,
  pageSize = 20,
  emptyText = '暂无数据',
}: DataTableProps<T>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize } },
  })

  if (error && !loading) {
    return (
      <div className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="flex flex-col items-center justify-center gap-3 px-6 py-16 text-center">
          <span className="grid h-9 w-9 place-items-center rounded-full bg-status-danger-soft text-status-danger">
            <TriangleAlert className="h-4 w-4" aria-hidden />
          </span>
          <p className="max-w-md text-[13px] text-foreground">{error}</p>
          {onRetry && (
            <Button size="sm" variant="outline" onClick={onRetry}>
              重试
            </Button>
          )}
        </div>
      </div>
    )
  }

  const rows = table.getRowModel().rows
  const total = table.getFilteredRowModel().rows.length
  const pageCount = table.getPageCount()
  const pageIndex = table.getState().pagination.pageIndex

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-table">
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id} className="border-b border-border bg-secondary text-left">
                {headerGroup.headers.map((header) => {
                  const canSort = header.column.getCanSort()
                  const sorted = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      className="whitespace-nowrap px-3 py-2 text-[12px] font-semibold text-muted-foreground"
                    >
                      {header.isPlaceholder ? null : canSort ? (
                        <button
                          type="button"
                          className="-mx-1 inline-flex items-center gap-1 rounded-sm px-1 py-0.5 transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/25"
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {sorted === 'asc' ? (
                            <ArrowUp className="h-3 w-3" aria-hidden />
                          ) : sorted === 'desc' ? (
                            <ArrowDown className="h-3 w-3" aria-hidden />
                          ) : (
                            <ChevronsUpDown className="h-3 w-3 opacity-40" aria-hidden />
                          )}
                        </button>
                      ) : (
                        flexRender(header.column.columnDef.header, header.getContext())
                      )}
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: Math.min(pageSize, 5) }).map((_, rowIndex) => (
                <tr key={rowIndex} className="border-b border-border last:border-0">
                  {columns.map((_, cellIndex) => (
                    <td key={cellIndex} className="px-3 py-2.5">
                      <div className="h-3.5 w-full max-w-[7rem] animate-pulse rounded-sm bg-accent" />
                    </td>
                  ))}
                </tr>
              ))
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length}>
                  <div className="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center">
                    <span className="grid h-9 w-9 place-items-center rounded-full bg-secondary text-muted-foreground">
                      <Inbox className="h-4 w-4" aria-hidden />
                    </span>
                    <p className="text-[13px] text-muted-foreground">{emptyText}</p>
                  </div>
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr
                  key={row.id}
                  className="border-b border-border transition-colors last:border-0 hover:bg-secondary/70"
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-3 py-2 align-middle text-foreground">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border bg-secondary/60 px-3 py-2">
        <span className="text-[12px] text-muted-foreground">
          共 <span className="num font-semibold text-foreground">{total}</span> 条
        </span>
        <div className="flex items-center gap-2">
          <select
            aria-label="每页条数"
            className="h-7 cursor-pointer rounded-md border border-input bg-card px-1.5 text-[12px] text-foreground shadow-sm outline-none transition-[border-color,box-shadow] hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"

            value={table.getState().pagination.pageSize}
            onChange={(event) => table.setPageSize(Number(event.target.value))}
          >
            {PAGE_SIZES.map((size) => (
              <option key={size} value={size}>
                {size} 条/页
              </option>
            ))}
          </select>
          <span className="num text-[12px] text-muted-foreground">
            第 {pageCount === 0 ? 0 : pageIndex + 1} / {pageCount} 页
          </span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => table.previousPage()}
            disabled={!table.getCanPreviousPage()}
          >
            上一页
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => table.nextPage()}
            disabled={!table.getCanNextPage()}
          >
            下一页
          </Button>
        </div>
      </div>
    </div>
  )
}
