import type { ReactNode } from 'react'

import { Inbox, LoaderCircle, TriangleAlert } from 'lucide-react'

import { Button } from '@/components/ui/button'

/** 页面/区块加载占位。 */
export function LoadingState({ text = '加载中…' }: { text?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-12 text-[13px] text-muted-foreground">
      <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden />
      {text}
    </div>
  )
}

/** 空状态：图标 + 一行结论。需要告诉用户下一步时传 hint / action。 */
export function EmptyState({
  text = '暂无数据',
  hint,
  action,
}: {
  text?: string
  hint?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <span className="grid h-9 w-9 place-items-center rounded-full bg-secondary text-muted-foreground/70">
        <Inbox className="h-4 w-4" aria-hidden />
      </span>
      <p className="text-[13px] font-semibold text-foreground">{text}</p>
      {hint && <p className="max-w-sm text-[13px] text-muted-foreground">{hint}</p>}
      {action}
    </div>
  )
}

/** 错误状态，可带重试。 */
export function ErrorState({
  message,
  onRetry,
  action,
}: {
  message: string
  onRetry?: () => void
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <span className="grid h-9 w-9 place-items-center rounded-full bg-status-danger-soft text-status-danger">
        <TriangleAlert className="h-4 w-4" aria-hidden />
      </span>
      <p className="max-w-md text-[13px] text-foreground">{message}</p>
      {action}
      {onRetry && (
        <Button size="sm" variant="outline" onClick={onRetry}>
          重试
        </Button>
      )}
    </div>
  )
}
