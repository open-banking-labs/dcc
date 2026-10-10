import { EmptyState } from '@/components/feedback/StateViews'
import { cn } from '@/lib/utils'
import type { ChangeKind, FieldChange, TableChangeSet } from '@/types'

function value(before: string | null): string {
  return before === null || before === '' ? '—' : before
}

const KIND_STYLE: Record<ChangeKind, { label: string; className: string }> = {
  ADDED: { label: '新增', className: 'border-green-300 bg-green-50 text-green-700' },
  REMOVED: { label: '删除', className: 'border-red-300 bg-red-50 text-red-700' },
  MODIFIED: { label: '修改', className: 'border-amber-300 bg-amber-50 text-amber-700' },
}

/** 字段变更列表（数标/接口/模板 diff）。 */
export function FieldChangeList({ changes }: { changes: FieldChange[] }) {
  if (changes.length === 0) {
    return <EmptyState text="两个版本内容一致" />
  }
  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full border-collapse text-table">
        <thead>
          <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
            <th className="px-3 py-2 font-medium">字段</th>
            <th className="px-3 py-2 font-medium">变更前</th>
            <th className="px-3 py-2 font-medium">变更后</th>
          </tr>
        </thead>
        <tbody>
          {changes.map((change) => (
            <tr key={change.field} className="border-b border-border align-top last:border-0">
              <td className="px-3 py-2 font-mono text-xs text-foreground">{change.field}</td>
              <td className="px-3 py-2 text-muted-foreground">{value(change.before)}</td>
              <td className="px-3 py-2 text-foreground">{value(change.after)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** 表结构 diff：表级 / 列级 / 索引。 */
export function TableChangeSetView({ changes }: { changes: TableChangeSet }) {
  const empty =
    changes.columns.length === 0 &&
    changes.tableFields.length === 0 &&
    changes.indexFields.length === 0
  if (empty) {
    return <EmptyState text="两个版本内容一致" />
  }

  return (
    <div className="space-y-4">
      {changes.tableFields.length > 0 && (
        <section>
          <h4 className="mb-1.5 text-sm font-medium text-foreground">表级属性</h4>
          <FieldChangeList changes={changes.tableFields} />
        </section>
      )}
      {changes.columns.length > 0 && (
        <section>
          <h4 className="mb-1.5 text-sm font-medium text-foreground">列</h4>
          <div className="space-y-2">
            {changes.columns.map((column) => (
              <div key={column.columnName} className="rounded-md border border-border">
                <div className="flex items-center gap-2 border-b border-border px-3 py-1.5">
                  <span className="font-mono text-xs text-foreground">{column.columnName}</span>
                  <span
                    className={cn(
                      'inline-flex items-center rounded-sm border px-1.5 py-0.5 text-xs leading-none',
                      KIND_STYLE[column.kind].className,
                    )}
                  >
                    {KIND_STYLE[column.kind].label}
                  </span>
                </div>
                {column.fields.length > 0 && <FieldChangeList changes={column.fields} />}
              </div>
            ))}
          </div>
        </section>
      )}
      {changes.indexFields.length > 0 && (
        <section>
          <h4 className="mb-1.5 text-sm font-medium text-foreground">索引</h4>
          <FieldChangeList changes={changes.indexFields} />
        </section>
      )}
    </div>
  )
}
