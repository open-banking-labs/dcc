import { Plus, Trash2 } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import type { TableColumn, TableIndex } from '@/types'

export interface StandardOption {
  id: number
  code: string
  name: string
}

const emptyColumn = (sortOrder: number): TableColumn => ({
  columnName: '',
  standardId: null,
  standardCode: null,
  dataType: null,
  length: null,
  scale: null,
  nullable: true,
  primaryKey: false,
  defaultValue: null,
  sortOrder,
  comment: null,
})

interface ColumnEditorProps {
  columns: TableColumn[]
  onChange: (columns: TableColumn[]) => void
  standards: StandardOption[]
}

/** 表列编辑器。列可引用数标（类型由后端按数标解析），或内联填写类型。 */
export function ColumnEditor({ columns, onChange, standards }: ColumnEditorProps) {
  const update = (index: number, patch: Partial<TableColumn>) => {
    onChange(columns.map((column, i) => (i === index ? { ...column, ...patch } : column)))
  }
  const remove = (index: number) => {
    onChange(
      columns.filter((_, i) => i !== index).map((column, i) => ({ ...column, sortOrder: i })),
    )
  }
  const add = () => onChange([...columns, emptyColumn(columns.length)])

  return (
    <div className="space-y-2">
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full border-collapse text-table">
          <thead>
            <tr className="border-b border-border bg-secondary text-left text-[12px] font-semibold text-muted-foreground">
              <th className="px-2 py-2 font-semibold">列名</th>
              <th className="px-2 py-2 font-semibold">引用数标</th>
              <th className="px-2 py-2 font-semibold">类型</th>
              <th className="px-2 py-2 font-semibold">长度</th>
              <th className="px-2 py-2 font-semibold">精度</th>
              <th className="px-2 py-2 font-semibold">可空</th>
              <th className="px-2 py-2 font-semibold">主键</th>
              <th className="px-2 py-2 font-semibold">默认值</th>
              <th className="px-2 py-2 font-semibold">注释</th>
              <th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {columns.length === 0 ? (
              <tr>
                <td colSpan={10} className="px-3 py-6 text-center text-sm text-muted-foreground">
                  暂无数据
                </td>
              </tr>
            ) : (
              columns.map((column, index) => (
                <tr key={index} className="border-b border-border last:border-0">
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={column.columnName}
                      onChange={(event) => update(index, { columnName: event.target.value })}
                    />
                  </td>
                  <td className="p-1">
                    <select
                      className="h-7 w-full cursor-pointer rounded-md border border-input bg-background px-1 text-[12px] text-foreground shadow-sm outline-none transition-[border-color,box-shadow] hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"
                      value={column.standardId ?? ''}
                      onChange={(event) => {
                        const value = event.target.value
                        if (!value) {
                          update(index, { standardId: null, standardCode: null })
                          return
                        }
                        const standard = standards.find((item) => item.id === Number(value))
                        update(index, {
                          standardId: Number(value),
                          standardCode: standard?.code ?? null,
                        })
                      }}
                    >
                      <option value="">内联</option>
                      {standards.map((standard) => (
                        <option key={standard.id} value={standard.id}>
                          {standard.code}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={column.dataType ?? ''}
                      disabled={column.standardId != null}
                      onChange={(event) => update(index, { dataType: event.target.value || null })}
                    />
                  </td>
                  <td className="p-1">
                    <Input
                      type="number"
                      className="h-7 w-16"
                      value={column.length ?? ''}
                      disabled={column.standardId != null}
                      onChange={(event) =>
                        update(index, {
                          length: event.target.value === '' ? null : Number(event.target.value),
                        })
                      }
                    />
                  </td>
                  <td className="p-1">
                    <Input
                      type="number"
                      className="h-7 w-16"
                      value={column.scale ?? ''}
                      disabled={column.standardId != null}
                      onChange={(event) =>
                        update(index, {
                          scale: event.target.value === '' ? null : Number(event.target.value),
                        })
                      }
                    />
                  </td>
                  <td className="p-1 text-center">
                    <Checkbox
                      checked={column.nullable}
                      onCheckedChange={(checked) => update(index, { nullable: checked === true })}
                    />
                  </td>
                  <td className="p-1 text-center">
                    <Checkbox
                      checked={column.primaryKey}
                      onCheckedChange={(checked) => update(index, { primaryKey: checked === true })}
                    />
                  </td>
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={column.defaultValue ?? ''}
                      onChange={(event) => update(index, { defaultValue: event.target.value || null })}
                    />
                  </td>
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={column.comment ?? ''}
                      onChange={(event) => update(index, { comment: event.target.value || null })}
                    />
                  </td>
                  <td className="p-1 text-center">
                    <Button type="button" size="sm" variant="ghost" aria-label="删除列" onClick={() => remove(index)}>
                      <Trash2 className="h-3.5 w-3.5" aria-hidden />
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <Button type="button" size="sm" variant="outline" onClick={add}>
        <Plus className="h-4 w-4" />
        添加列
      </Button>
    </div>
  )
}

interface IndexEditorProps {
  indexes: TableIndex[]
  onChange: (indexes: TableIndex[]) => void
}

/** 表索引编辑器。索引列以逗号分隔的列名填写。 */
export function IndexEditor({ indexes, onChange }: IndexEditorProps) {
  const update = (index: number, patch: Partial<TableIndex>) => {
    onChange(indexes.map((item, i) => (i === index ? { ...item, ...patch } : item)))
  }
  const remove = (index: number) => onChange(indexes.filter((_, i) => i !== index))
  const add = () => onChange([...indexes, { name: '', unique: false, columns: [] }])

  return (
    <div className="space-y-2">
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full border-collapse text-table">
          <thead>
            <tr className="border-b border-border bg-secondary text-left text-[12px] font-semibold text-muted-foreground">
              <th className="px-2 py-2 font-semibold">索引名</th>
              <th className="px-2 py-2 font-semibold">唯一</th>
              <th className="px-2 py-2 font-semibold">列（逗号分隔）</th>
              <th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {indexes.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-3 py-6 text-center text-sm text-muted-foreground">
                  暂无数据
                </td>
              </tr>
            ) : (
              indexes.map((index, i) => (
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={index.name}
                      onChange={(event) => update(i, { name: event.target.value })}
                    />
                  </td>
                  <td className="p-1 text-center">
                    <Checkbox
                      checked={index.unique}
                      onCheckedChange={(checked) => update(i, { unique: checked === true })}
                    />
                  </td>
                  <td className="p-1">
                    <Input
                      className="h-7"
                      value={index.columns.join(', ')}
                      onChange={(event) =>
                        update(i, {
                          columns: event.target.value
                            .split(',')
                            .map((name) => name.trim())
                            .filter(Boolean),
                        })
                      }
                    />
                  </td>
                  <td className="p-1 text-center">
                    <Button type="button" size="sm" variant="ghost" aria-label="删除索引" onClick={() => remove(i)}>
                      <Trash2 className="h-3.5 w-3.5" aria-hidden />
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <Button type="button" size="sm" variant="outline" onClick={add}>
        <Plus className="h-4 w-4" />
        添加索引
      </Button>
    </div>
  )
}
