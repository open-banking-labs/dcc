import type { OnChangeFn, PaginationState, RowSelectionState } from '@tanstack/react-table'
import { ArrowRightLeft, Pencil, Plus, Trash2, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PermissionGate } from '@/components/common/PermissionGate'
import { FilterBar } from '@/components/data/FilterBar'
import { useToast } from '@/components/feedback/Toast'
import { Field, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { ImportDialog } from '@/components/standards/ImportDialog'
import { StandardsTable } from '@/components/standards/StandardsTable'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useAsync } from '@/hooks/useAsync'
import { useListRestoration } from '@/hooks/useListRestoration'
import { useResourceContext } from '@/hooks/useResourceContext'
import { STANDARD_STATUS_LABEL } from '@/lib/labels'
import { PERMISSION } from '@/lib/permissions'
import { STANDARD_TRANSITIONS, standardStatusOptions } from '@/lib/status'
import type { StandardStatus } from '@/types'

const DEFAULT_PAGE_SIZE = 20

/**
 * 数标维护：新建/导入入口 + 单选的逐条维护清单（编辑、删除、状态流转）。
 * 查询与阅读仍在“数标查询”，这里只承载写操作。
 */
export function StandardsMaintenancePage() {
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
  const [importOpen, setImportOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [transitionOpen, setTransitionOpen] = useState(false)
  const [transitionTarget, setTransitionTarget] = useState('')
  const [transitioning, setTransitioning] = useState(false)

  useEffect(() => {
    setDraft({ keyword, category, status })
  }, [keyword, category, status])

  // Single-row maintenance: a selection belongs to the page/filter/scope it was made on.
  useEffect(() => {
    setRowSelection({})
  }, [environmentId, applicationId, keyword, category, status, pageIndex, pageSize])

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
  const currentUrl = location.pathname + location.search
  const selectedId = Object.keys(rowSelection).length > 0 ? Number(Object.keys(rowSelection)[0]) : null
  const selectedRow = rows.find((row) => row.id === selectedId) ?? null
  const transitionTargets = selectedRow ? STANDARD_TRANSITIONS[selectedRow.status] : []

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

  const openEdit = () => {
    if (selectedId == null) return
    navigate(`/standards/${selectedId}/edit`, { state: { from: currentUrl } })
  }

  const openTransition = () => {
    if (!selectedRow || transitionTargets.length === 0) return
    setTransitionTarget(transitionTargets[0])
    setTransitionOpen(true)
  }

  const doDelete = async () => {
    if (selectedId == null) return
    setDeleting(true)
    try {
      await standardsApi.remove(selectedId)
      toast.success('已删除')
      setDeleteOpen(false)
      setRowSelection({})
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  const doTransition = async () => {
    if (selectedId == null || !transitionTarget) return
    setTransitioning(true)
    try {
      await standardsApi.transition(selectedId, transitionTarget as StandardStatus)
      toast.success('状态已更新')
      setTransitionOpen(false)
      setRowSelection({})
      reload()
    } catch (err) {
      toast.error('状态流转失败', err instanceof Error ? err.message : undefined)
    } finally {
      setTransitioning(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="数标维护"
        description="新建、导入、逐条编辑、删除与状态流转；检索与历史版本请使用「数标查询」。"
      />

      <FilterBar onSearch={applyFilters} onReset={resetFilters}>
        <Field label="编码/名称" htmlFor="maintain-keyword" className="w-64">
          <TextField
            id="maintain-keyword"
            value={draft.keyword}
            onChange={(value) => setDraft((prev) => ({ ...prev, keyword: value }))}
            placeholder="支持模糊查询"
          />
        </Field>
        <Field label="分类" htmlFor="maintain-category" className="w-44">
          <TextField
            id="maintain-category"
            value={draft.category}
            onChange={(value) => setDraft((prev) => ({ ...prev, category: value }))}
            placeholder="按分类筛选"
          />
        </Field>
        <Field label="状态" htmlFor="maintain-status" className="w-40">
          <SelectField
            id="maintain-status"
            value={draft.status}
            onChange={(value) => setDraft((prev) => ({ ...prev, status: value }))}
            options={standardStatusOptions()}
            placeholder="全部"
          />
        </Field>
      </FilterBar>

      <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-card px-3 py-2">
        <span className="mr-1 text-[13px] text-muted-foreground">
          已选 <span className="num font-semibold text-foreground">{selectedId == null ? 0 : 1}</span> 条
        </span>
        <PermissionGate authority={PERMISSION.artifactWrite}>
          <Button size="sm" variant="primary" onClick={() => navigate('/standards/new')}>
            <Plus className="h-4 w-4" />
            新建
          </Button>
        </PermissionGate>
        <PermissionGate authority={PERMISSION.artifactWrite}>
          <Button size="sm" variant="outline" onClick={() => setImportOpen(true)}>
            <Upload className="h-4 w-4" />
            导入
          </Button>
        </PermissionGate>
        <PermissionGate authority={PERMISSION.artifactWrite}>
          <Button size="sm" variant="outline" disabled={selectedId == null} onClick={openEdit}>
            <Pencil className="h-4 w-4" />
            编辑
          </Button>
        </PermissionGate>
        <PermissionGate authority={PERMISSION.artifactDelete}>
          <Button
            size="sm"
            variant="outline"
            className="text-destructive hover:text-destructive"
            disabled={selectedId == null}
            onClick={() => setDeleteOpen(true)}
          >
            <Trash2 className="h-4 w-4" />
            删除
          </Button>
        </PermissionGate>
        <PermissionGate authority={PERMISSION.artifactTransition}>
          <Button
            size="sm"
            variant="outline"
            disabled={selectedId == null || transitionTargets.length === 0}
            onClick={openTransition}
          >
            <ArrowRightLeft className="h-4 w-4" />
            状态流转
          </Button>
        </PermissionGate>
        {selectedId != null && (
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
        selectionMode="single"
        rowSelection={rowSelection}
        onRowSelectionChange={setRowSelection}
        pagination={{ pageIndex, pageSize }}
        onPaginationChange={onPaginationChange}
        pageCount={pageCount}
        total={total}
      />

      <ImportDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        environmentId={environmentId}
        applicationId={applicationId}
        onImported={() => {
          setRowSelection({})
          reload()
        }}
      />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除数标"
        description={
          selectedRow
            ? `确认删除「${selectedRow.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。`
            : undefined
        }
        loading={deleting}
        onConfirm={() => void doDelete()}
      />

      <Dialog open={transitionOpen} onOpenChange={setTransitionOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>状态流转</DialogTitle>
            <DialogDescription>
              {selectedRow
                ? `${selectedRow.code} 当前状态：${STANDARD_STATUS_LABEL[selectedRow.status]}`
                : undefined}
            </DialogDescription>
          </DialogHeader>
          <Field label="目标状态" htmlFor="transition-target">
            <SelectField
              id="transition-target"
              value={transitionTarget}
              onChange={setTransitionTarget}
              options={transitionTargets.map((target) => ({
                value: target,
                label: STANDARD_STATUS_LABEL[target],
              }))}
              placeholder="选择目标状态"
            />
          </Field>
          <DialogFooter>
            <Button size="sm" variant="outline" onClick={() => setTransitionOpen(false)} disabled={transitioning}>
              取消
            </Button>
            <Button
              size="sm"
              onClick={() => void doTransition()}
              disabled={transitioning || !transitionTarget}
            >
              {transitioning ? '处理中…' : '确认流转'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
