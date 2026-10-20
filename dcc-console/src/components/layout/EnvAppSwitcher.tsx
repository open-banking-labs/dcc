import { ChevronDown } from 'lucide-react'

import { useSession } from '@/auth/session'
import { useResourceContext } from '@/hooks/useResourceContext'
import { PERMISSION } from '@/lib/permissions'

/** 全局"环境 / 应用"切换器：列表与新增都从这里取上下文。 */
export function EnvAppSwitcher() {
  const { permissions } = useSession()
  const {
    environments,
    applications,
    environmentId,
    applicationId,
    setEnvironmentId,
    setApplicationId,
    loading,
  } = useResourceContext()

  // 平台管理员没有租户拓扑权限：不渲染无意义的空选择器。
  if (!permissions.includes(PERMISSION.topologyRead)) return null

  const selectClass =
    'h-7 max-w-[4rem] cursor-pointer appearance-none rounded-md bg-transparent pl-1 pr-5 text-[12px] font-medium text-white outline-none transition-colors hover:bg-white/10 focus-visible:bg-white/10 focus-visible:ring-2 focus-visible:ring-white/30 disabled:cursor-not-allowed disabled:opacity-50 sm:max-w-[7rem] lg:max-w-[8.5rem] xl:max-w-[9rem] [&>option]:bg-background [&>option]:text-foreground'

  return (
    <div className="flex items-center gap-0.5 rounded-md border border-white/20 bg-white/5 px-1">
      <label className="relative flex min-w-0 items-center gap-1.5 pl-1">
        <span className="hidden text-[12px] font-semibold text-header-muted xl:inline">环境</span>
        <select
          aria-label="当前环境"
          className={selectClass}
          value={environmentId ?? ''}
          disabled={loading}
          onChange={(event) =>
            setEnvironmentId(event.target.value ? Number(event.target.value) : null)
          }
        >
          {environments.length === 0 && <option value="">—</option>}
          {environments.map((env) => (
            <option key={env.id} value={env.id}>
              {env.name}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-0.5 top-1/2 h-3 w-3 -translate-y-1/2 text-header-muted"
          aria-hidden
        />
      </label>
      <span aria-hidden className="h-4 w-px bg-white/15" />
      <label className="relative flex min-w-0 items-center gap-1.5 pl-1.5 pr-1">
        <span className="hidden text-[12px] font-semibold text-header-muted xl:inline">应用</span>
        <select
          aria-label="当前应用"
          className={selectClass}
          value={applicationId ?? ''}
          disabled={loading}
          onChange={(event) =>
            setApplicationId(event.target.value ? Number(event.target.value) : null)
          }
        >
          {applications.length === 0 && <option value="">—</option>}
          {applications.map((app) => (
            <option key={app.id} value={app.id}>
              {app.name}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-1.5 top-1/2 h-3 w-3 -translate-y-1/2 text-header-muted"
          aria-hidden
        />
      </label>
    </div>
  )
}
