import type { ReactNode } from 'react'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'

/** 字段容器：标签 + 控件 + 错误/提示。 */
export function Field({
  label,
  htmlFor,
  required,
  error,
  hint,
  className,
  children,
}: {
  label: string
  htmlFor?: string
  required?: boolean
  error?: string
  hint?: string
  className?: string
  children: ReactNode
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="ml-0.5 text-destructive">*</span>}
      </Label>
      {children}
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : (
        hint && <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}

export function TextField({
  id,
  value,
  onChange,
  placeholder,
  disabled,
  type = 'text',
}: {
  id?: string
  value: string | null
  onChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  type?: string
}) {
  return (
    <Input
      id={id}
      type={type}
      value={value ?? ''}
      placeholder={placeholder}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

export function NumberField({
  id,
  value,
  onChange,
  placeholder,
  disabled,
}: {
  id?: string
  value: number | null
  onChange: (value: number | null) => void
  placeholder?: string
  disabled?: boolean
}) {
  return (
    <Input
      id={id}
      type="number"
      value={value ?? ''}
      placeholder={placeholder}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
    />
  )
}

export function TextAreaField({
  id,
  value,
  onChange,
  placeholder,
  disabled,
  rows,
}: {
  id?: string
  value: string | null
  onChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  rows?: number
}) {
  return (
    <Textarea
      id={id}
      rows={rows}
      value={value ?? ''}
      placeholder={placeholder}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

export interface SelectOption {
  value: string
  label: string
}

export function SelectField({
  id,
  value,
  onChange,
  options,
  placeholder,
  disabled,
}: {
  id?: string
  value: string | null
  onChange: (value: string) => void
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
}) {
  return (
    <select
      id={id}
      value={value ?? ''}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
      className="flex h-8 w-full cursor-pointer rounded-md border border-input bg-background px-2.5 text-[13px] text-foreground shadow-sm transition-[border-color,box-shadow] outline-none hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:bg-secondary disabled:opacity-60"
    >
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

/** 两列/多列布局里的字段行。 */
export function FieldRow({ columns = 2, children }: { columns?: 1 | 2 | 3; children: ReactNode }) {
  const colsClass = columns === 3 ? 'sm:grid-cols-3' : columns === 2 ? 'sm:grid-cols-2' : 'grid-cols-1'
  return <div className={cn('grid grid-cols-1 gap-3', colsClass)}>{children}</div>
}
