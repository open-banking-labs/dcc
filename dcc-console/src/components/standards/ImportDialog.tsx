import { Download } from 'lucide-react'
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
import type { StandardImportResult } from '@/types'

interface ImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  environmentId: number | null
  applicationId: number | null
  onImported: () => void
}

/**
 * 带模板的 Excel 导入：先下载模板，填写后上传。后端整表校验通过才写入，
 * 有错误时按 Excel 行号返回原因，便于手工修正后重试。
 */
export function ImportDialog({
  open,
  onOpenChange,
  environmentId,
  applicationId,
  onImported,
}: ImportDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<StandardImportResult | null>(null)

  useEffect(() => {
    if (!open) return
    setFile(null)
    setUploading(false)
    setDownloading(false)
    setError(null)
    setResult(null)
  }, [open])

  const downloadTemplate = async () => {
    setDownloading(true)
    setError(null)
    try {
      const blob = await standardsApi.importTemplate()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = '数标导入模板.xlsx'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : '下载模板失败')
    } finally {
      setDownloading(false)
    }
  }

  const upload = async () => {
    if (environmentId == null || applicationId == null) {
      setError('请先选择环境与应用')
      return
    }
    if (!file) {
      setError('请选择要导入的 .xlsx 文件')
      return
    }
    setUploading(true)
    setError(null)
    setResult(null)
    try {
      const imported = await standardsApi.importExcel(environmentId, applicationId, file)
      setResult(imported)
      if (imported.errors.length === 0) onImported()
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败')
    } finally {
      setUploading(false)
    }
  }

  const noScope = environmentId == null || applicationId == null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>导入数标</DialogTitle>
          <DialogDescription>
            按编码匹配：当前环境/应用下已存在则更新，不存在则新建。单次最多 2000 行。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <Button size="sm" variant="outline" onClick={() => void downloadTemplate()} disabled={downloading}>
              <Download className="h-4 w-4" />
              {downloading ? '下载中…' : '下载导入模板'}
            </Button>
            <span className="text-muted-foreground">填写模板后再上传，不要修改表头。</span>
          </div>

          <input
            type="file"
            accept=".xlsx"
            disabled={noScope || uploading}
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            className="block w-full cursor-pointer rounded-md border border-input bg-card px-3 py-2 text-sm text-foreground file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1 file:text-sm file:text-foreground"
          />

          {noScope && <p className="text-destructive">请先在顶栏选择环境与应用。</p>}

          <div className="flex items-center gap-2">
            <Button size="sm" onClick={() => void upload()} disabled={uploading || noScope || !file}>
              {uploading ? '导入中…' : '开始导入'}
            </Button>
            {file && <span className="truncate text-xs text-muted-foreground">{file.name}</span>}
          </div>

          {error && <p className="break-words text-destructive">{error}</p>}

          {result && result.errors.length === 0 && (
            <p className="rounded-md border border-status-success/30 bg-status-success-soft px-3 py-2 text-foreground">
              导入完成：新建 {result.created} 条，更新 {result.updated} 条。
            </p>
          )}

          {result && result.errors.length > 0 && (
            <div className="space-y-2">
              <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-destructive">
                有 {result.errors.length} 行未通过校验，本次未导入任何数据。请修正后重试。
              </p>
              <div className="max-h-56 overflow-y-auto rounded-md border border-border">
                <table className="w-full border-collapse text-xs">
                  <thead className="bg-secondary text-left text-muted-foreground">
                    <tr>
                      <th className="px-2 py-1.5 font-medium">行号</th>
                      <th className="px-2 py-1.5 font-medium">原因</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.errors.map((rowError) => (
                      <tr key={`${rowError.row}-${rowError.message}`} className="border-t border-border">
                        <td className="num px-2 py-1.5">{rowError.row}</td>
                        <td className="px-2 py-1.5 text-foreground">{rowError.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
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
