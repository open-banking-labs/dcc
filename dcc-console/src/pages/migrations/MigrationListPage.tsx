import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { migrationsApi } from '@/api/migrations'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { FilterBar } from '@/components/data/FilterBar'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, NumberField, SelectField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { PermissionGate } from '@/components/common/PermissionGate'
import { PERMISSION } from '@/lib/permissions'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { formatDateTime } from '@/lib/format'
import { ARTIFACT_TYPE_LABEL, MIGRATION_STATUS_LABEL } from '@/lib/labels'
import type { ArtifactType, MigrationOrderView, MigrationStatus } from '@/types'

const ARTIFACT_TYPES: ArtifactType[] = [
  'DATA_STANDARD',
  'TABLE_STRUCTURE',
  'INTERFACE',
  'INTERFACE_TEMPLATE',
]

const MIGRATION_STATUSES: MigrationStatus[] = [
  'PENDING_SUBMISSION',
  'PENDING_APPROVAL',
  'APPROVED',
  'MIGRATED',
  'REJECTED',
  'ROLLED_BACK',
]

interface CreateForm {
  artifactType: string
  artifactId: number | null
  sourceEnvironmentId: string
  targetEnvironmentId: string
  note: string
}

const EMPTY_CREATE: CreateForm = {
  artifactType: 'DATA_STANDARD',
  artifactId: null,
  sourceEnvironmentId: '',
  targetEnvironmentId: '',
  note: '',
}

export function MigrationListPage() {
  const toast = useToast()
  const { environments } = useResourceContext()

  const [status, setStatus] = useState('')
  const [targetEnv, setTargetEnv] = useState('')
  const [applied, setApplied] = useState({ status: '', targetEnv: '' })

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState<CreateForm>(EMPTY_CREATE)
  const [createErrors, setCreateErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const { data, loading, error, reload } = useAsync(
    () =>
      migrationsApi.list({
        status: (applied.status || undefined) as MigrationStatus | undefined,
        targetEnvironmentId: applied.targetEnv ? Number(applied.targetEnv) : undefined,
      }),
    [applied],
  )
  const rows = data ?? []

  const envOptions = environments.map((env) => ({ value: String(env.id), label: env.name }))
  const envName = (id: number) => environments.find((env) => env.id === id)?.name ?? `#${id}`

  const columns: ColumnDef<MigrationOrderView, any>[] = [
    { accessorKey: 'id', header: 'ID', cell: ({ row }) => `#${row.original.id}` },
    {
      id: 'artifactType',
      header: '产物类型',
      accessorFn: (row) => row.artifactType,
      cell: ({ row }) => ARTIFACT_TYPE_LABEL[row.original.artifactType],
    },
    {
      id: 'artifactCode',
      header: '产物',
      accessorFn: (row) => row.artifactCode,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.artifactCode}</span>,
    },
    {
      id: 'sourceVersion',
      header: '源版本',
      accessorFn: (row) => row.sourceVersion,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.sourceVersion}</span>,
    },
    {
      id: 'target',
      header: '目标环境',
      accessorFn: (row) => row.targetEnvironmentId,
      cell: ({ row }) => envName(row.original.targetEnvironmentId),
    },
    {
      id: 'status',
      header: '状态',
      accessorFn: (row) => row.status,
      cell: ({ row }) => <StatusBadge kind="migration" status={row.original.status} />,
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
        <div className="flex justify-end">
          <Button asChild size="sm" variant="ghost">
            <Link to={`/migrations/${row.original.id}`}>查看</Link>
          </Button>
        </div>
      ),
    },
  ]

  const openCreate = () => {
    setCreateForm({
      ...EMPTY_CREATE,
      sourceEnvironmentId: environments[0] ? String(environments[0].id) : '',
      targetEnvironmentId: environments[1] ? String(environments[1].id) : '',
    })
    setCreateErrors({})
    setCreateOpen(true)
  }

  const submitCreate = async () => {
    const next: Record<string, string> = {}
    if (createForm.artifactId == null) next.artifactId = '产物 ID 不能为空'
    if (!createForm.sourceEnvironmentId) next.sourceEnvironmentId = '请选择源环境'
    if (!createForm.targetEnvironmentId) next.targetEnvironmentId = '请选择目标环境'
    if (createForm.sourceEnvironmentId && createForm.sourceEnvironmentId === createForm.targetEnvironmentId) {
      next.targetEnvironmentId = '源环境与目标环境必须不同'
    }
    setCreateErrors(next)
    if (Object.keys(next).length > 0) return

    setSubmitting(true)
    try {
      const order = await migrationsApi.create({
        artifactType: createForm.artifactType as ArtifactType,
        artifactId: Number(createForm.artifactId),
        sourceEnvironmentId: Number(createForm.sourceEnvironmentId),
        targetEnvironmentId: Number(createForm.targetEnvironmentId),
        note: createForm.note.trim() || null,
      })
      toast.success('已创建迁移单', `#${order.id}`)
      setCreateOpen(false)
      reload()
    } catch (err) {
      toast.error('创建失败', err instanceof Error ? err.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="迁移单"
        description="环境间迁移及其审批流程"
        actions={
          <PermissionGate authority={PERMISSION.migrationWrite}>
          <Button size="sm" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            新建
          </Button>
          </PermissionGate>
        }
      />

      <FilterBar
        onSearch={() => setApplied({ status, targetEnv })}
        onReset={() => {
          setStatus('')
          setTargetEnv('')
          setApplied({ status: '', targetEnv: '' })
        }}
      >
        <Field label="状态" htmlFor="filter-status" className="w-40">
          <SelectField
            id="filter-status"
            value={status}
            onChange={setStatus}
            options={MIGRATION_STATUSES.map((value) => ({
              value,
              label: MIGRATION_STATUS_LABEL[value],
            }))}
            placeholder="全部"
          />
        </Field>
        <Field label="目标环境" htmlFor="filter-target" className="w-40">
          <SelectField
            id="filter-target"
            value={targetEnv}
            onChange={setTargetEnv}
            options={envOptions}
            placeholder="全部"
          />
        </Field>
      </FilterBar>

      <DataTable
        columns={columns}
        data={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="暂无数据"
      />

      <FormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        title="新建迁移单"
        onSubmit={() => void submitCreate()}
        submitting={submitting}
        submitText="创建"
      >
        <Field label="产物类型" htmlFor="c-type">
          <SelectField
            id="c-type"
            value={createForm.artifactType}
            onChange={(value) => setCreateForm((prev) => ({ ...prev, artifactType: value }))}
            options={ARTIFACT_TYPES.map((value) => ({ value, label: ARTIFACT_TYPE_LABEL[value] }))}
          />
        </Field>
        <Field label="产物 ID" htmlFor="c-id" required error={createErrors.artifactId}>
          <NumberField
            id="c-id"
            value={createForm.artifactId}
            onChange={(value) => setCreateForm((prev) => ({ ...prev, artifactId: value }))}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="源环境" htmlFor="c-src" required error={createErrors.sourceEnvironmentId}>
            <SelectField
              id="c-src"
              value={createForm.sourceEnvironmentId}
              onChange={(value) => setCreateForm((prev) => ({ ...prev, sourceEnvironmentId: value }))}
              options={envOptions}
              placeholder="选择"
            />
          </Field>
          <Field label="目标环境" htmlFor="c-dst" required error={createErrors.targetEnvironmentId}>
            <SelectField
              id="c-dst"
              value={createForm.targetEnvironmentId}
              onChange={(value) => setCreateForm((prev) => ({ ...prev, targetEnvironmentId: value }))}
              options={envOptions}
              placeholder="选择"
            />
          </Field>
        </div>
        <Field label="备注" htmlFor="c-note">
          <TextField
            id="c-note"
            value={createForm.note}
            onChange={(value) => setCreateForm((prev) => ({ ...prev, note: value }))}
          />
        </Field>
      </FormDialog>
    </div>
  )
}
