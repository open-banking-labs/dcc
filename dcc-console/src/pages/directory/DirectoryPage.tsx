import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'

import { directoryApi, type AssignmentView, type MembershipView } from '@/api/directory'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { DataTable } from '@/components/data/DataTable'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAsync } from '@/hooks/useAsync'
import { formatDateTime } from '@/lib/format'

const PAGE_SIZE = 20

interface DirectoryFilters {
  subject: string
  tenant: string
  role: string
}

const EMPTY_FILTERS: DirectoryFilters = { subject: '', tenant: '', role: '' }

function MembershipPane({ filters }: { filters: DirectoryFilters }) {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const subject = filters.subject || null
  const tenant = filters.tenant || null

  const { data, loading, error, reload } = useAsync(
    () => directoryApi.memberships.search(subject, tenant, page, PAGE_SIZE),
    [subject, tenant, page],
  )

  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ subject: '', tenantCode: '' })
  const [removing, setRemoving] = useState<MembershipView | null>(null)
  const [busy, setBusy] = useState(false)

  const create = async () => {
    setBusy(true)
    try {
      await directoryApi.memberships.create({ subject: form.subject.trim(), tenantCode: form.tenantCode.trim() })
      toast.success('已授权成员', form.subject.trim())
      setCreating(false)
      setForm({ subject: '', tenantCode: '' })
      setPage(0)
      reload()
    } catch (err) {
      toast.error('授权失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!removing) return
    setBusy(true)
    try {
      await directoryApi.memberships.remove(removing.id)
      toast.success('已收回成员权限', removing.subject)
      setRemoving(null)
      reload()
    } catch (err) {
      toast.error('收回失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const columns: ColumnDef<MembershipView, any>[] = [
    { id: 'subject', header: '操作者', accessorFn: (row) => row.subject, cell: ({ row }) => (
      <span className="font-mono text-xs">{row.original.subject}</span>
    ) },
    { id: 'tenantCode', header: '租户', accessorFn: (row) => row.tenantCode },
    { id: 'createdAt', header: '授权时间', accessorFn: (row) => row.createdAt, cell: ({ row }) => (
      <span className="whitespace-nowrap text-muted-foreground">{formatDateTime(row.original.createdAt)}</span>
    ) },
    { id: 'actions', header: '操作', enableSorting: false, cell: ({ row }) => (
      <div className="flex justify-end">
        <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive"
          onClick={() => setRemoving(row.original)}>
          收回
        </Button>
      </div>
    ) },
  ]

  const pageData = data as { content: MembershipView[]; totalElements?: number; totalPages?: number } | null

  return (
    <>
      <div className="flex items-center justify-between p-3">
        <p className="text-xs text-muted-foreground">
          成员关系决定「这个操作者能不能进这个租户」；directory-mode 开启后即生效。
        </p>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus className="h-4 w-4" />授权成员
        </Button>
      </div>
      <DataTable columns={columns} data={pageData?.content ?? []} loading={loading} error={error} onRetry={reload}
        pageSize={PAGE_SIZE} emptyText="该租户暂无成员关系" />
      <Pager page={page} totalPages={pageData?.totalPages ?? 1} totalElements={pageData?.totalElements ?? 0} onPage={setPage} />

      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="授权成员"
        description="授予一个操作者进入指定租户的资格（成员关系按 (租户, 操作者) 唯一）。"
        submitText="授权"
        submitting={busy}
        onSubmit={() => void create()}
      >
        <Field label="操作者 (sub)" htmlFor="dir-membership-subject" required>
          <TextField id="dir-membership-subject" value={form.subject} onChange={(value) => setForm({ ...form, subject: value })} placeholder="如 IdP 的 sub" />
        </Field>
        <Field label="租户 code" htmlFor="dir-membership-tenant" required>
          <TextField id="dir-membership-tenant" value={form.tenantCode} onChange={(value) => setForm({ ...form, tenantCode: value })} placeholder="如 default_tenant" />
        </Field>
      </FormDialog>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => { if (!open) setRemoving(null) }}
        title="收回成员权限"
        description={removing ? `确认收回 ${removing.subject} 在 ${removing.tenantCode} 的成员资格？删除为逻辑删除，可在审计日志页恢复。` : undefined}
        confirmText="收回"
        loading={busy}
        onConfirm={() => void remove()}
      />
    </>
  )
}

function AssignmentPane({ filters }: { filters: DirectoryFilters }) {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const subject = filters.subject || null
  const tenant = filters.tenant || null
  const role = filters.role || null

  const { data, loading, error, reload } = useAsync(
    () => directoryApi.assignments.search(subject, tenant, role, page, PAGE_SIZE),
    [subject, tenant, role, page],
  )

  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ subject: '', tenantCode: '', role: '', scopeType: 'TENANT', scopeCode: '' })
  const [removing, setRemoving] = useState<AssignmentView | null>(null)
  const [busy, setBusy] = useState(false)

  const create = async () => {
    setBusy(true)
    try {
      await directoryApi.assignments.create({
        subject: form.subject.trim(),
        tenantCode: form.tenantCode.trim(),
        role: form.role.trim(),
        scopeType: form.scopeType as AssignmentView['scopeType'],
        scopeCode: form.scopeType === 'TENANT' ? null : form.scopeCode.trim(),
      })
      toast.success('已指派角色', `${form.subject.trim()} → ${form.role.trim()}`)
      setCreating(false)
      setForm({ subject: '', tenantCode: '', role: '', scopeType: 'TENANT', scopeCode: '' })
      setPage(0)
      reload()
    } catch (err) {
      toast.error('指派失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!removing) return
    setBusy(true)
    try {
      await directoryApi.assignments.remove(removing.id)
      toast.success('已收回角色', `${removing.subject} 的 ${removing.role}`)
      setRemoving(null)
      reload()
    } catch (err) {
      toast.error('收回失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const columns: ColumnDef<AssignmentView, any>[] = [
    { id: 'subject', header: '操作者', accessorFn: (row) => row.subject, cell: ({ row }) => (
      <span className="font-mono text-xs">{row.original.subject}</span>
    ) },
    { id: 'tenantCode', header: '租户', accessorFn: (row) => row.tenantCode },
    { id: 'role', header: '角色', accessorFn: (row) => row.role, cell: ({ row }) => (
      <span className="font-medium text-foreground">{row.original.role}</span>
    ) },
    { id: 'scope', header: '范围', accessorFn: (row) => `${row.scopeType}:${row.scopeCode ?? ''}`, cell: ({ row }) => (
      <span className="whitespace-nowrap text-xs text-muted-foreground">
        {row.original.scopeType === 'TENANT' ? '租户全域' : `${row.original.scopeType === 'ENVIRONMENT' ? '环境' : '应用'} · ${row.original.scopeCode}`}
      </span>
    ) },
    { id: 'createdAt', header: '授予时间', accessorFn: (row) => row.createdAt, cell: ({ row }) => (
      <span className="whitespace-nowrap text-muted-foreground">{formatDateTime(row.original.createdAt)}</span>
    ) },
    { id: 'actions', header: '操作', enableSorting: false, cell: ({ row }) => (
      <div className="flex justify-end">
        <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive"
          onClick={() => setRemoving(row.original)}>
          收回
        </Button>
      </div>
    ) },
  ]

  const pageData = data as { content: AssignmentView[]; totalElements?: number; totalPages?: number } | null

  return (
    <>
      <div className="flex items-center justify-between p-3">
        <p className="text-xs text-muted-foreground">
          角色指派经 role-authorities 映射后与 claim 角色并集生效；只增不减，收回走 claim 侧。
        </p>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus className="h-4 w-4" />指派角色
        </Button>
      </div>
      <DataTable columns={columns} data={pageData?.content ?? []} loading={loading} error={error} onRetry={reload}
        pageSize={PAGE_SIZE} emptyText="暂无角色指派" />
      <Pager page={page} totalPages={pageData?.totalPages ?? 1} totalElements={pageData?.totalElements ?? 0} onPage={setPage} />

      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="指派角色"
        description="授予租户全域或指定环境/应用的角色；角色名会再走 dcc.security.role-authorities 映射。"
        submitText="指派"
        submitting={busy}
        onSubmit={() => void create()}
      >
        <Field label="操作者 (sub)" htmlFor="dir-assignment-subject" required>
          <TextField id="dir-assignment-subject" value={form.subject} onChange={(value) => setForm({ ...form, subject: value })} />
        </Field>
        <Field label="租户 code" htmlFor="dir-assignment-tenant" required>
          <TextField id="dir-assignment-tenant" value={form.tenantCode} onChange={(value) => setForm({ ...form, tenantCode: value })} />
        </Field>
        <Field label="角色" htmlFor="dir-assignment-role" required>
          <TextField id="dir-assignment-role" value={form.role} onChange={(value) => setForm({ ...form, role: value })} placeholder="如 DCC_ADMIN / editor" />
        </Field>
        <Field label="授权范围" htmlFor="dir-assignment-scope" required>
          <SelectField id="dir-assignment-scope" value={form.scopeType}
            onChange={(value) => setForm({ ...form, scopeType: value, scopeCode: value === 'TENANT' ? '' : form.scopeCode })}
            options={[
              { value: 'TENANT', label: '租户全域' },
              { value: 'ENVIRONMENT', label: '指定环境' },
              { value: 'APPLICATION', label: '指定应用' },
            ]} />
        </Field>
        {form.scopeType !== 'TENANT' && (
          <Field label={form.scopeType === 'ENVIRONMENT' ? '环境 code' : '应用 code'}
            htmlFor="dir-assignment-scope-code" required
            hint="必须是该租户中已存在的业务 code。">
            <TextField id="dir-assignment-scope-code" value={form.scopeCode}
              onChange={(value) => setForm({ ...form, scopeCode: value })} />
          </Field>
        )}
      </FormDialog>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => { if (!open) setRemoving(null) }}
        title="收回角色"
        description={removing ? `确认收回 ${removing.subject} 在 ${removing.tenantCode} 的 ${removing.role}？删除为逻辑删除，可在审计日志页恢复。` : undefined}
        confirmText="收回"
        loading={busy}
        onConfirm={() => void remove()}
      />
    </>
  )
}

function Pager({ page, totalPages, totalElements, onPage }: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}) {
  return (
    <div className="flex items-center justify-end gap-3 px-3 pb-1 text-sm text-muted-foreground">
      <span>共 {totalElements} 条 · 第 {page + 1}/{Math.max(1, totalPages)} 页</span>
      <Button size="sm" variant="outline" disabled={page === 0} onClick={() => onPage(page - 1)}>
        上一页
      </Button>
      <Button size="sm" variant="outline" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        下一页
      </Button>
    </div>
  )
}

/** 授权目录（管理员）：directory-mode 的门槛与超出 claim 的授信都在这里维护。 */
export function DirectoryPage() {
  const [draft, setDraft] = useState<DirectoryFilters>(EMPTY_FILTERS)
  const [applied, setApplied] = useState<DirectoryFilters>(EMPTY_FILTERS)

  const apply = () => setApplied(draft)
  const reset = () => {
    setDraft(EMPTY_FILTERS)
    setApplied(EMPTY_FILTERS)
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="授权目录"
        description="按 (操作者, 租户) 管理准入成员与角色指派；Tel 需要管理员权限，所有变更进审计"
      />

      <div className="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-3">
        <Field label="操作者" className="w-44">
          <TextField value={draft.subject} onChange={(value) => setDraft({ ...draft, subject: value })} placeholder="精确匹配" />
        </Field>
        <Field label="租户" className="w-40">
          <TextField value={draft.tenant} onChange={(value) => setDraft({ ...draft, tenant: value })} placeholder="精确匹配" />
        </Field>
        <Field label="角色（仅指派）" className="w-32">
          <TextField value={draft.role} onChange={(value) => setDraft({ ...draft, role: value })} placeholder="精确匹配" />
        </Field>
        <div className="ml-auto flex items-center gap-2">
          <Button size="sm" onClick={apply}>查询</Button>
          <Button size="sm" variant="outline" onClick={reset}>重置</Button>
        </div>
      </div>

      <Tabs defaultValue="memberships">
        <TabsList>
          <TabsTrigger value="memberships">租户成员</TabsTrigger>
          <TabsTrigger value="assignments">角色指派</TabsTrigger>
        </TabsList>
        <TabsContent value="memberships">
          <MembershipPane filters={applied} />
        </TabsContent>
        <TabsContent value="assignments">
          <AssignmentPane filters={applied} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
