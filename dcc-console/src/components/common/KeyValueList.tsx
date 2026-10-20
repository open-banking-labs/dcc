import type { ReactNode } from 'react'

/** 详情页概览：标签/值两列网格。 */
export function KeyValueList({ items }: { items: { label: string; value: ReactNode }[] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-10 gap-y-3.5 sm:grid-cols-2">
      {items.map((item) => (
        <div key={item.label} className="flex flex-col gap-1">
          <dt className="text-[12px] font-semibold text-muted-foreground">{item.label}</dt>
          <dd className="break-words text-[13px] text-foreground">{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}
