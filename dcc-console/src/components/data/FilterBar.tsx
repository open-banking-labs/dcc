import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'

/** 筛选栏容器：字段区 + 右侧查询/重置。 */
export function FilterBar({
  children,
  onSearch,
  onReset,
}: {
  children: ReactNode
  onSearch: () => void
  onReset: () => void
}) {
  return (
    <div className="flex flex-wrap items-end gap-x-4 gap-y-3 rounded-lg border border-border bg-secondary/60 px-4 py-3">
      {children}
      <div className="ml-auto flex items-center gap-2">
        <Button size="sm" onClick={onSearch}>
          查询
        </Button>
        <Button size="sm" variant="outline" onClick={onReset}>
          重置
        </Button>
      </div>
    </div>
  )
}
