import type { ReactNode } from 'react'

/** 页面/区块加载占位。 */
export function LoadingState({ text = '加载中…' }: { text?: string }) {
  return (
    <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">{text}</div>
  )
}

/** 空状态：固定文案"暂无数据"。 */
export function EmptyState({ text = '暂无数据' }: { text?: string }) {
  return (
    <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">{text}</div>
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
    <div className="flex flex-col items-center justify-center gap-2 py-12">
      <p className="text-sm text-destructive">{message}</p>
      {action}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-md border border-input bg-background px-2.5 py-1 text-sm hover:bg-accent"
        >
          重试
        </button>
      )}
    </div>
  )
}
