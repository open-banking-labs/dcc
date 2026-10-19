import { useState } from 'react'
import { useParams } from 'react-router-dom'

import { migrationsApi } from '@/api/migrations'
import { KeyValueList } from '@/components/common/KeyValueList'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { useSession } from '@/auth/session'
import { dash, formatDateTime } from '@/lib/format'
import { ARTIFACT_TYPE_LABEL } from '@/lib/labels'
import { PERMISSION } from '@/lib/permissions'
import type { MigrationStatus } from '@/types'

interface Action {
  key: 'submit' | 'approve' | 'reject' | 'execute' | 'rollback'
  label: string
  authority?: string
  variant?: 'default' | 'outline' | 'destructive'
}

const ACTIONS: Record<MigrationStatus, Action[]> = {
  PENDING_SUBMISSION: [{ key: 'submit', label: '提交', authority: PERMISSION.migrationWrite }],
  PENDING_APPROVAL: [
    { key: 'approve', label: '批准', authority: PERMISSION.migrationApprove },
    { key: 'reject', label: '拒绝', authority: PERMISSION.migrationApprove, variant: 'outline' },
  ],
  APPROVED: [{ key: 'execute', label: '执行', authority: PERMISSION.migrationExecute }],
  MIGRATED: [{ key: 'rollback', label: '回滚', authority: PERMISSION.migrationExecute, variant: 'outline' }],
  REJECTED: [],
  ROLLED_BACK: [],
}

export function MigrationDetailPage() {
  const { id } = useParams()
  const numericId = Number(id)
  const toast = useToast()
  const { environments } = useResourceContext()
  const session = useSession()
  const permissions = session.permissions ?? session.roles
  const canApprove = permissions.includes(PERMISSION.migrationApprove)

  const { data: order, loading, error, reload } = useAsync(
    () => migrationsApi.get(numericId),
    [numericId],
  )
  const [busy, setBusy] = useState(false)

  const envName = (envId: number) => environments.find((env) => env.id === envId)?.name ?? `#${envId}`

  const doAction = async (key: Action['key'], label: string) => {
    setBusy(true)
    try {
      await migrationsApi[key](numericId)
      toast.success(`${label}成功`)
      reload()
    } catch (err) {
      toast.error(`${label}失败`, err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <div className="p-6">
        <LoadingState />
      </div>
    )
  }
  if (error) {
    return (
      <div className="rounded-md border border-border bg-card">
        <ErrorState message={error} onRetry={reload} />
      </div>
    )
  }
  if (!order) {
    return <LoadingState text="暂无数据" />
  }

  const actions = ACTIONS[order.status].filter(
    (action) => !action.authority || permissions.includes(action.authority),
  )

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title={`迁移单 #${order.id}`}
        description={`${ARTIFACT_TYPE_LABEL[order.artifactType]} · ${order.artifactCode}`}
        actions={
          actions.length > 0 ? (
            <>
              {actions.map((action) => (
                <Button
                  key={action.key}
                  size="sm"
                  variant={action.variant ?? 'default'}
                  disabled={busy}
                  onClick={() => void doAction(action.key, action.label)}
                >
                  {action.label}
                </Button>
              ))}
            </>
          ) : undefined
        }
      />

      <div className="rounded-md border border-border bg-card p-4">
        <h3 className="mb-3 text-sm font-medium text-foreground">基本信息</h3>
        <KeyValueList
          items={[
            { label: '状态', value: <StatusBadge kind="migration" status={order.status} /> },
            { label: '产物类型', value: ARTIFACT_TYPE_LABEL[order.artifactType] },
            { label: '产物编码', value: <span className="font-mono text-xs">{order.artifactCode}</span> },
            { label: '源版本', value: <span className="font-mono text-xs">{order.sourceVersion}</span> },
            { label: '源环境', value: envName(order.sourceEnvironmentId) },
            { label: '目标环境', value: envName(order.targetEnvironmentId) },
            { label: '目标产物 ID', value: dash(order.targetArtifactId) },
            { label: '目标版本', value: dash(order.targetVersion) },
            { label: '创建时间', value: formatDateTime(order.createdAt) },
            { label: '更新时间', value: formatDateTime(order.updatedAt) },
            { label: '备注', value: dash(order.note) },
          ]}
        />
      </div>

      {order.status === 'PENDING_APPROVAL' && !canApprove && (
        <p className="text-sm text-muted-foreground">当前状态：待审批（需要审批权限才能批准或拒绝）</p>
      )}
    </div>
  )
}
