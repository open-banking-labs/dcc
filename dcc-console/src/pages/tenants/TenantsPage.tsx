import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'

import { tenantsApi } from '@/api/tenancy'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/data/DataTable'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { PermissionGate } from '@/components/common/PermissionGate'
import { PERMISSION } from '@/lib/permissions'
import { Checkbox } from '@/components/ui/checkbox'
import { useAsync } from '@/hooks/useAsync'
import { dash, formatDateTime } from '@/lib/format'
import type { TenantView } from '@/types'

interface FormState {
  code: string
  name: string
  description: string
  enabled: boolean
}

const EMPTY: FormState = { code: '', name: '', description: '', enabled: true }

export function TenantsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => tenantsApi.list(), [])
  const rows = data ?? []

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<TenantView | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<TenantView | null>(null)
  const [deleting, setDeleting] = useState(false)

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY)
    setErrors({})
    setDialogOpen(true)
  }
  const openEdit = (tenant: TenantView) => {
    setEditing(tenant)
    setForm({
      code: tenant.code,
      name: tenant.name,
      description: tenant.description ?? '',
      enabled: tenant.enabled,
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
        await tenantsApi.update(editing.code, {
          name: form.name.trim(),
          description: form.description.trim(),
          enabled: form.enabled,
        })
        toast.success('已保存', editing.code)
      } else {
        await tenantsApi.create({
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim() || null,
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
      await tenantsApi.remove(deleteTarget.code)
      toast.success('已删除', deleteTarget.code)
      setDeleteTarget(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDeleting(false)
    }
  }

  const columns: ColumnDef<TenantView, any>[] = [
    {
      accessorKey: 'code',
      header: '编码',
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.code}</span>,
    },
    { id: 'name', header: '名称', accessorFn: (row) => row.name },
    {
      id: 'enabled',
      header: '启用',
      accessorFn: (row) => row.enabled,
      cell: ({ row }) => (row.original.enabled ? '是' : '否'),
    },
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
        <PermissionGate authority={PERMISSION.tenantAdmit}>
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
        </PermissionGate>
      ),
    },
  ]

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="租户"
        description="平台级租户管理（隔离根）"
        actions={
          <PermissionGate authority={PERMISSION.tenantAdmit}>
          <Button size="sm" variant="primary" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            新建
          </Button>
          </PermissionGate>
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
        title={editing ? '编辑租户' : '新建租户'}
        onSubmit={() => void submit()}
        submitting={submitting}
      >
        <Field label="编码" htmlFor="t-code" required={!editing} error={errors.code}>
          <TextField
            id="t-code"
            value={form.code}
            onChange={(value) => setForm((prev) => ({ ...prev, code: value }))}
            disabled={editing !== null}
          />
        </Field>
        <Field label="名称" htmlFor="t-name" required error={errors.name}>
          <TextField
            id="t-name"
            value={form.name}
            onChange={(value) => setForm((prev) => ({ ...prev, name: value }))}
          />
        </Field>
        <Field label="描述" htmlFor="t-desc">
          <TextField
            id="t-desc"
            value={form.description}
            onChange={(value) => setForm((prev) => ({ ...prev, description: value }))}
          />
        </Field>
        {editing && (
          <label className="flex items-center gap-2 text-sm text-foreground">
            <Checkbox
              checked={form.enabled}
              onCheckedChange={(checked) => setForm((prev) => ({ ...prev, enabled: checked === true }))}
            />
            启用
          </label>
        )}
      </FormDialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
        title="删除租户"
        description={deleteTarget ? `确认删除「${deleteTarget.code}」？删除为逻辑删除（合规保留），可稍后在「审计日志」页恢复。` : undefined}
        loading={deleting}
        onConfirm={() => void doDelete()}
      />
    </div>
  )
}
