import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'

import { environmentsApi } from '@/api/tenancy'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/data/DataTable'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, NumberField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { dash, formatDateTime } from '@/lib/format'
import type { EnvironmentView } from '@/types'

interface FormState {
  code: string
  name: string
  description: string
  sortOrder: number | null
}

const EMPTY: FormState = { code: '', name: '', description: '', sortOrder: null }

export function EnvironmentsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => environmentsApi.list(), [])
  const rows = data ?? []

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<EnvironmentView | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<EnvironmentView | null>(null)
  const [deleting, setDeleting] = useState(false)

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY)
    setErrors({})
    setDialogOpen(true)
  }
  const openEdit = (env: EnvironmentView) => {
    setEditing(env)
    setForm({
      code: env.code,
      name: env.name,
      description: env.description ?? '',
      sortOrder: env.sortOrder,
    })
    setErrors({})
    setDialogOpen(true)
  }

  const submit = async () => {
    const next: Record<string, string> = {}
    if (!editing && !form.code.trim()) next.code = '编码不能为空'
    if (!form.name.trim()) next.name = '名称不能为空'
    setErrors(next)
    if (Object.keys(next).length > 0) return

    setSubmitting(true)
    try {
      if (editing) {
        await environmentsApi.update(editing.id, {
          name: form.name.trim(),
          description: form.description.trim(),
          sortOrder: form.sortOrder ?? 0,
        })
        toast.success('已保存', editing.code)
      } else {
        await environmentsApi.create({
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim() || null,
          sortOrder: form.sortOrder ?? 0,
        })
        toast.success('已创建', form.code)
      }
      setDialogOpen(false)
      reload()
    } catch (err) {
      toast.error('保存失败', err instanceof Error ? err.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  const doDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await environmentsApi.remove(deleteTarget.id)
      toast.success('已删除', deleteTarget.code)
      setDeleteTarget(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  const columns: ColumnDef<EnvironmentView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.code}</span>,
    },
    { id: 'name', header: '名称', accessorFn: (row) => row.name },
    { id: 'sortOrder', header: '排序', accessorFn: (row) => row.sortOrder },
    {
      id: 'description',
      header: '描述',
      accessorFn: (row) => row.description ?? '',
      cell: ({ row }) => dash(row.original.description),
    },
    {
      accessorKey: 'updatedAt',
      header: '更新时间',
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-muted-foreground">
          {formatDateTime(row.original.updatedAt)}
        </span>
      ),
    },
    {
      id: 'actions',
      header: '操作',
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex justify-end gap-1">
          <Button size="sm" variant="ghost" onClick={() => openEdit(row.original)}>
            编辑
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="text-destructive hover:text-destructive"
            onClick={() => setDeleteTarget(row.original)}
          >
            删除
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="环境"
        description="租户内的环境（DEV / TEST / PROD，可自定义）"
        actions={
          <Button size="sm" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            新建
          </Button>
        }
      />

      <DataTable
        columns={columns}
        data={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="暂无数据"
      />

      <FormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        title={editing ? '编辑环境' : '新建环境'}
        onSubmit={() => void submit()}
        submitting={submitting}
      >
        <Field label="编码" htmlFor="e-code" required={!editing} error={errors.code}>
          <TextField
            id="e-code"
            value={form.code}
            onChange={(value) => setForm((prev) => ({ ...prev, code: value }))}
            disabled={editing !== null}
            placeholder="如 DEV / TEST / PROD"
          />
        </Field>
        <Field label="名称" htmlFor="e-name" required error={errors.name}>
          <TextField
            id="e-name"
            value={form.name}
            onChange={(value) => setForm((prev) => ({ ...prev, name: value }))}
          />
        </Field>
        <Field label="排序" htmlFor="e-sort">
          <NumberField
            id="e-sort"
            value={form.sortOrder}
            onChange={(value) => setForm((prev) => ({ ...prev, sortOrder: value }))}
          />
        </Field>
        <Field label="描述" htmlFor="e-desc">
          <TextField
            id="e-desc"
            value={form.description}
            onChange={(value) => setForm((prev) => ({ ...prev, description: value }))}
          />
        </Field>
      </FormDialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
        title="删除环境"
        description={deleteTarget ? `确认删除「${deleteTarget.code}」？该操作不可恢复。` : undefined}
        loading={deleting}
        onConfirm={() => void doDelete()}
      />
    </div>
  )
}
