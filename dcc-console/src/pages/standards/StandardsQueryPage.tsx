import type { OnChangeFn, PaginationState, RowSelectionState } from '@tanstack/react-table'
import { FileSpreadsheet, History, ListTree } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { FilterBar } from '@/components/data/FilterBar'
import { useToast } from '@/components/feedback/Toast'
import { Field, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { ExportDialog } from '@/components/standards/ExportDialog'
import { StandardsTable } from '@/components/standards/StandardsTable'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useListRestoration } from '@/hooks/useListRestoration'
import { useResourceContext } from '@/hooks/useResourceContext'
import { standardStatusOptions } from '@/lib/status'
import type { StandardStatus } from '@/types'

const DEFAULT_PAGE_SIZE = 20
const MAX_ANALYSIS_SELECTION = 100

/**
 * 数标查询：只做检索、引用分析与历史版本查看。无操作列；所有批量入口都在表格上方，
 * 筛选、页码、页大小写入 URL，配合 useListRestoration 在往返详情后恢复现场。
 */
export function StandardsQueryPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const { environmentId, applicationId } = useResourceContext()

  const keyword = searchParams.get('keyword') ?? ''
  const category = searchParams.get('category') ?? ''
  const status = searchParams.get('status') ?? ''
  const pageIndex = Math.max(0, Number(searchParams.get('page') ?? '0') || 0)
  const pageSize = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE)) || DEFAULT_PAGE_SIZE

  const [draft, setDraft] = useState({ keyword, category, status })
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [exportOpen, setExportOpen] = useState(false)

  useEffect(() => {
    setDraft({ keyword, category, status })
  }, [keyword, category, status])

  // A selection belongs to one environment/application scope.
  useEffect(() => {
    setRowSelection({})
  }, [environmentId, applicationId])

  const { data, loading, error, reload } = useAsync(
    () =>
      standardsApi.page({
        environmentId: environmentId ?? undefined,
        applicationId: applicationId ?? undefined,
        keyword: keyword || undefined,
        category: category || undefined,
        status: (status || undefined) as StandardStatus | undefined,
        page: pageIndex,
        size: pageSize,
      }),
    [environmentId, applicationId, keyword, category, status, pageIndex, pageSize],
  )

  useListRestoration(!loading)

  const rows = data?.content ?? []
  const total = data?.totalElements ?? 0
  const pageCount = data?.totalPages ?? 0
  const selectedIds = Object.keys(rowSelection).map(Number)
  const currentUrl = location.pathname + location.search

  const writeParams = (next: {
    keyword: string
    category: string
    status: string
    page: number
    size: number
  }) => {
    const params = new URLSearchParams()
    if (next.keyword) params.set('keyword', next.keyword)
    if (next.category) params.set('category', next.category)
    if (next.status) params.set('status', next.status)
    if (next.page > 0) params.set('page', String(next.page))
    if (next.size !== DEFAULT_PAGE_SIZE) params.set('size', String(next.size))
    setSearchParams(params, { replace: true })
  }

  const applyFilters = () => {
    setRowSelection({})
    writeParams({
      keyword: draft.keyword.trim(),
      category: draft.category.trim(),
      status: draft.status,
      page: 0,
      size: pageSize,
    })
  }

  const resetFilters = () => {
    setDraft({ keyword: '', category: '', status: '' })
    setRowSelection({})
    writeParams({ keyword: '', category: '', status: '', page: 0, size: pageSize })
  }

  const onPaginationChange: OnChangeFn<PaginationState> = (updater) => {
    const next = typeof updater === 'function' ? updater({ pageIndex, pageSize }) : updater
    writeParams({ keyword, category, status, page: next.pageIndex, size: next.pageSize })
  }

  const openReferenceAnalysis = () => {
    if (selectedIds.length === 0) return
    if (selectedIds.length > MAX_ANALYSIS_SELECTION) {
      toast.error('选择过多', `引用分析最多选择 ${MAX_ANALYSIS_SELECTION} 条`)
      return
    }
    navigate(
      `/standards/reference-analysis?ids=${selectedIds.join(',')}&from=${encodeURIComponent(currentUrl)}`,
    )
  }

  const openHistory = () => {
    if (selectedIds.length !== 1) return
    navigate(`/standards/${selectedIds[0]}?tab=history`, { state: { from: currentUrl } })
  }

  const openExport = () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setExportOpen(true)
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="数标查询"
        description="按编码或名称检索数标，查看引用关系与历史版本；查询结果可导出 Excel。"
      />

      <FilterBar onSearch={applyFilters} onReset={resetFilters}>
        <Field label="编码/名称" htmlFor="filter-keyword" className="w-64">
          <TextField
            id="filter-keyword"
            value={draft.keyword}
            onChange={(value) => setDraft((prev) => ({ ...prev, keyword: value }))}
            placeholder="支持模糊查询"
          />
        </Field>
        <Field label="分类" htmlFor="filter-category" className="w-44">
          <TextField
            id="filter-category"
            value={draft.category}
            onChange={(value) => setDraft((prev) => ({ ...prev, category: value }))}
            placeholder="按分类筛选"
          />
        </Field>
        <Field label="状态" htmlFor="filter-status" className="w-40">
          <SelectField
            id="filter-status"
            value={draft.status}
            onChange={(value) => setDraft((prev) => ({ ...prev, status: value }))}
            options={standardStatusOptions()}
            placeholder="全部"
          />
        </Field>
      </FilterBar>

      <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-card px-3 py-2">
        <span className="mr-1 text-[13px] text-muted-foreground">
          已选 <span className="num font-semibold text-foreground">{selectedIds.length}</span> 条
        </span>
        <Button size="sm" variant="outline" disabled={selectedIds.length === 0} onClick={openReferenceAnalysis}>
          <ListTree className="h-4 w-4" />
          引用分析
        </Button>
        <Button size="sm" variant="outline" disabled={selectedIds.length !== 1} onClick={openHistory}>
          <History className="h-4 w-4" />
          查看历史版本
        </Button>
        <Button size="sm" variant="outline" onClick={openExport}>
          <FileSpreadsheet className="h-4 w-4" />
          导出 Excel
        </Button>
        {selectedIds.length > 0 && (
          <Button size="sm" variant="ghost" onClick={() => setRowSelection({})}>
            清空选择
          </Button>
        )}
      </div>

      <StandardsTable
        rows={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        returnTo={currentUrl}
        selectionMode="multiple"
        rowSelection={rowSelection}
        onRowSelectionChange={setRowSelection}
        pagination={{ pageIndex, pageSize }}
        onPaginationChange={onPaginationChange}
        pageCount={pageCount}
        total={total}
      />

      <ExportDialog
        open={exportOpen}
        onOpenChange={setExportOpen}
        environmentId={environmentId}
        applicationId={applicationId}
        keyword={keyword}
        category={category}
        status={status ? (status as StandardStatus) : undefined}
        selectedIds={selectedIds}
      />
    </div>
  )
}
