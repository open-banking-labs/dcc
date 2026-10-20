import type { ReactNode } from 'react'

/** 页面标题栏：标题 + 可选描述 + 右侧操作区。 */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
      <div className="min-w-0">
        <h1 className="break-words text-title text-foreground">{title}</h1>
        {description && <p className="mt-1 max-w-2xl break-words text-meta">{description}</p>}
      </div>
      {actions && <div className="flex min-w-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
