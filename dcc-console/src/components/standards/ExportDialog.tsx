import { Download, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'

import { standardsApi } from '@/api/standards'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatDateTime } from '@/lib/format'
import type { StandardExportJob, StandardExportStatus, StandardStatus } from '@/types'

const STATUS_LABEL: Record<StandardExportStatus, string> = {
  PENDING: '排队中',
  RUNNING: '生成中',
  READY: '已完成',
  FAILED: '失败',
  EXPIRED: '已过期',
}

interface ExportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  environmentId: number | null
  applicationId: number | null
  keyword?: string
  category?: string
  status?: StandardStatus
  selectedIds: number[]
}

/**
 * 异步导出：先创建任务，再轮询状态；READY 后直接用预签名链接从对象存储下载，
 * 文件不经过应用服务器。链接 10 分钟有效，可随时“刷新链接”。
 */
export function ExportDialog({
  open,
  onOpenChange,
  environmentId,
  applicationId,
  keyword,
  category,
  status,
  selectedIds,
}: ExportDialogProps) {
  const [job, setJob] = useState<StandardExportJob | null>(null)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setJob(null)
    setCreating(false)
    setError(null)
  }, [open])

  useEffect(() => {
    if (!open || !job || (job.status !== 'PENDING' && job.status !== 'RUNNING')) return
    let cancelled = false
    const timer = window.setTimeout(async () => {
      try {
        const next = await standardsApi.exportJob(job.id)
        if (!cancelled) setJob(next)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : '查询导出状态失败')
      }
    }, 1500)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [open, job])

  const start = async () => {
    if (environmentId == null || applicationId == null) {
      setError('请先选择环境与应用')
      return
    }
    setCreating(true)
    setError(null)
    try {
      const created = await standardsApi.createExport({
        environmentId,
        applicationId,
        keyword: keyword || null,
        category: category || null,
        status: status ?? null,
        ids: selectedIds.length > 0 ? selectedIds : null,
      })
      setJob(created)
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建导出任务失败')
    } finally {
      setCreating(false)
    }
  }

  const refreshLink = async () => {
    if (!job) return
    try {
      setJob(await standardsApi.exportJob(job.id))
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '刷新链接失败')
    }
  }

  const scopeText =
    selectedIds.length > 0
      ? `导出所选 ${selectedIds.length} 条数标`
      : '导出当前筛选结果（最多 50000 行）'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>导出数标</DialogTitle>
          <DialogDescription>
            后台生成 Excel，完成后提供限时下载链接，文件不经过应用服务器。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 text-sm">
          <p className="text-muted-foreground">{scopeText}</p>

          {!job && (
            <Button size="sm" onClick={() => void start()} disabled={creating}>
              {creating ? '创建中…' : '开始导出'}
            </Button>
          )}

          {job && (
            <div className="space-y-2 rounded-md border border-border bg-secondary/40 p-3">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium text-foreground">{STATUS_LABEL[job.status]}</span>
                <span className="font-mono text-xs text-muted-foreground">#{job.id}</span>
              </div>
              {job.status === 'PENDING' || job.status === 'RUNNING' ? (
                <p className="text-muted-foreground">正在生成，请稍候…</p>
              ) : null}
              {job.status === 'READY' && job.downloadUrl && (
                <>
                  <p className="text-muted-foreground">
                    共 {job.rowCount ?? 0} 条；链接有效至 {formatDateTime(job.expiresAt)}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Button asChild size="sm">
                      <a href={job.downloadUrl} target="_blank" rel="noreferrer">
                        <Download className="h-4 w-4" />
                        下载 Excel
                      </a>
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => void refreshLink()}>
                      <RefreshCw className="h-4 w-4" />
                      刷新链接
                    </Button>
                  </div>
                </>
              )}
              {job.status === 'EXPIRED' && (
                <p className="text-muted-foreground">文件已过期，请重新发起导出。</p>
              )}
              {job.status === 'FAILED' && (
                <p className="break-words text-destructive">{job.errorMessage ?? '导出失败'}</p>
              )}
            </div>
          )}

          {error && <p className="break-words text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button size="sm" variant="outline" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
