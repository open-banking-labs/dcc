import { useResourceContext } from '@/hooks/useResourceContext'

/** 全局"环境 / 应用"切换器：列表与新增都从这里取上下文。 */
export function EnvAppSwitcher() {
  const {
    environments,
    applications,
    environmentId,
    applicationId,
    setEnvironmentId,
    setApplicationId,
    loading,
  } = useResourceContext()

  const selectClass =
    'h-7 max-w-[10rem] rounded-md border border-input bg-background px-1.5 text-xs text-foreground disabled:opacity-50'

  return (
    <div className="flex items-center gap-2">
      <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
        环境
        <select
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
      </label>
      <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
        应用
        <select
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
      </label>
    </div>
  )
}
