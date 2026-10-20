import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { migrationsApi } from '@/api/migrations'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { FilterBar } from '@/components/data/FilterBar'
import { PageHeader } from '@/components/layout/PageHeader'
import { ApplicationMigrationDialog } from '@/components/migration/ApplicationMigrationDialog'
import { Button } from '@/components/ui/button'
import { PermissionGate } from '@/components/common/PermissionGate'
import { PERMISSION } from '@/lib/permissions'
import { Field, SelectField } from '@/components/form/fields'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { formatDateTime } from '@/lib/format'
import { ARTIFACT_TYPE_LABEL, MIGRATION_STATUS_LABEL } from '@/lib/labels'
import type { MigrationOrderView, MigrationStatus } from '@/types'

const MIGRATION_STATUSES: MigrationStatus[] = [
  'PENDING_SUBMISSION',
  'PENDING_APPROVAL',
  'APPROVED',
  'MIGRATED',
  'REJECTED',
  'ROLLED_BACK',
]

export function MigrationListPage() {
  const { environments, applicationId } = useResourceContext()

  const [status, setStatus] = useState('')
  const [targetEnv, setTargetEnv] = useState('')
  const [applied, setApplied] = useState({ status: '', targetEnv: '' })

  const [createOpen, setCreateOpen] = useState(false)

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

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="迁移单"
        description="环境间迁移及其审批流程（以应用为单位发起）"
        actions={
          <PermissionGate authority={PERMISSION.migrationWrite}>
            <Button
              size="sm"
              onClick={() => setCreateOpen(true)}
              disabled={applicationId == null}
              title={applicationId == null ? '请先在顶栏选择应用' : undefined}
            >
              <Plus className="h-4 w-4" />
              发起迁移
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

      <ApplicationMigrationDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        applicationId={applicationId}
        onCreated={reload}
      />
    </div>
  )
}
