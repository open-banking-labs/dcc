import type { ReactNode } from 'react'

import { Link } from 'react-router-dom'

import { dashboardApi } from '@/api/dashboard'
import { useSession } from '@/auth/session'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { PageHeader } from '@/components/layout/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { useResourceContext } from '@/hooks/useResourceContext'
import { useAsync } from '@/hooks/useAsync'
import { ARTIFACT_TYPE_LABEL } from '@/lib/labels'
import { cn } from '@/lib/utils'
import type { DashboardSummary, MigrationSummary } from '@/types/dashboard'

/** 一张指标卡：大数字 + 副标题 + 可选的待办强调。 */
function StatCard({
  label,
  value,
  hint,
  pending,
  to,
}: {
  label: string
  value: number | string
  hint?: string
  pending?: number
  to?: string
}) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <span className="text-[13px] font-medium text-muted-foreground">{label}</span>
        {pending !== undefined && pending > 0 && (
          <span className="inline-flex shrink-0 items-center gap-1 rounded-full border border-status-warning/30 bg-status-warning-soft px-1.5 py-[3px] text-[12px] font-semibold leading-none text-status-warning">
            <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-status-warning" />
            {pending} 待审
          </span>
        )}
      </div>
      <div className="mt-2 flex items-baseline gap-2">
        <span className="num text-[28px] font-semibold leading-none tracking-tight text-foreground">
          {value}
        </span>
      </div>
      {hint && <div className="mt-2 text-[12px] text-muted-foreground">{hint}</div>}
    </>
  )
  const className = 'rounded-lg border border-border bg-card p-4 transition-colors'
  return to ? (
    <Link
      to={to}
      className={cn(
        className,
        'block hover:border-muted-foreground/40 hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25',
      )}
    >
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  )
}

function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="space-y-2.5">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-section text-foreground">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

const ARTIFACT_CARDS: { key: keyof NonNullable<DashboardSummary['artifacts']>; label: string; to: string }[] = [
  { key: 'dataStandard', label: '数标', to: '/standards' },
  { key: 'tableStructure', label: '表结构', to: '/tables' },
  { key: 'interfaces', label: '接口', to: '/interfaces' },
  { key: 'interfaceTemplates', label: '接口模板', to: '/templates' },
]

const MIGRATION_ROWS: { key: keyof MigrationSummary; label: string }[] = [
  { key: 'pendingSubmission', label: '待提交' },
  { key: 'pendingApproval', label: '待审批' },
  { key: 'approved', label: '已批准' },
  { key: 'migrated', label: '已迁移' },
  { key: 'rejected', label: '已拒绝' },
  { key: 'rolledBack', label: '已回滚' },
]

/**
 * 工作台：登录后的落点。
 *
 * 卡片由服务端返回的 section 决定显隐（无权的 section 是 null），所以这里不判断权限点，
 * 只判断「有没有这一段」。环境/应用来自顶栏切换器，切换即重新拉取。
 */
export function DashboardPage() {
  const { displayName } = useSession()
  const { environmentId, applicationId, environments, applications } = useResourceContext()
  const { data, loading, error, reload } = useAsync<DashboardSummary>(
    () => dashboardApi.summary({ environmentId, applicationId }),
    [environmentId, applicationId],
  )

  const envName = environments.find((env) => env.id === environmentId)?.name
  const appName = applications.find((app) => app.id === applicationId)?.name
  const scope = [envName ?? '全部环境', appName ?? '全部应用'].join(' · ')

  if (loading) return <div className="p-6"><LoadingState /></div>
  if (error) return <div className="p-6"><ErrorState message={error} onRetry={reload} /></div>
  if (!data) return <div className="p-6"><LoadingState /></div>

  const hasAnything = Boolean(data.artifacts || data.migrations || data.audit || data.tenants)

  return (
    <div className="space-y-7 p-6">
      <PageHeader title="工作台" description={`你好，${displayName} · 当前范围 ${scope}`} />

      {!hasAnything && (
        <div className="rounded-lg border border-border bg-card">
          <EmptyState
            text="当前账号没有可展示的看板数据"
            hint="从左侧菜单进入你有权限的模块；需要更多权限时请联系管理员。"
          />
        </div>
      )}

      {data.artifacts && (
        <Section
          title="数据资产"
          action={<span className="text-[12px] text-muted-foreground">总量 / 待审</span>}
        >
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {ARTIFACT_CARDS.map((card) => (
              <StatCard
                key={card.key}
                label={card.label}
                value={data.artifacts![card.key].total}
                pending={data.artifacts![card.key].pendingReview}
                to={card.to}
              />
            ))}
          </div>
        </Section>
      )}

      {data.migrations && (
        <Section
          title="变更与发布"
          action={
            <Link
              to="/migrations"
              className="text-[13px] font-medium text-primary underline-offset-2 hover:underline"
            >
              全部迁移单
            </Link>
          }
        >
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {MIGRATION_ROWS.map((row) => (
              <StatCard
                key={row.key}
                label={row.label}
                value={data.migrations![row.key]}
                hint={row.key === 'pendingApproval' && data.migrations!.pendingApproval > 0 ? '待四眼审批' : undefined}
              />
            ))}
          </div>
        </Section>
      )}

      {data.recentMigrations && data.recentMigrations.length > 0 && (
        <Section title="最近迁移单">
          <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border bg-card">
            {data.recentMigrations.map((order) => (
              <li key={order.id}>
                <Link
                  to={`/migrations/${order.id}`}
                  className="flex items-center justify-between gap-3 px-4 py-2.5 transition-colors hover:bg-secondary/70 focus-visible:outline-none focus-visible:ring-inset focus-visible:ring-2 focus-visible:ring-ring/25"
                >
                  <span className="flex min-w-0 items-center gap-2.5">
                    <span className="shrink-0 text-[12px] text-muted-foreground">
                      {ARTIFACT_TYPE_LABEL[order.artifactType]}
                    </span>
                    <span className="truncate text-[13px] text-foreground">
                      {order.artifactCode ?? `#${order.id}`}
                    </span>
                  </span>
                  <span className="flex shrink-0 items-center gap-3">
                    <span className="num whitespace-nowrap text-[12px] text-muted-foreground">
                      {new Date(order.updatedAt).toLocaleString()}
                    </span>
                    <StatusBadge status={order.status} kind="migration" />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Section>
      )}

      {(data.audit || data.tenants) && (
        <Section title="平台与审计">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {data.audit && <StatCard label="近 24 小时审计" value={data.audit.last24h} hint="当前可读的审计范围" />}
            {data.tenants && <StatCard label="租户" value={data.tenants.total} hint={`${data.tenants.enabled} 个启用`} />}
          </div>
        </Section>
      )}
    </div>
  )
}
