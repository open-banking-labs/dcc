import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { applicationsApi, environmentsApi } from '@/api/tenancy'
import { useSession } from '@/auth/session'
import { ResourceContext, type ResourceContextValue } from '@/hooks/useResourceContext'
import { PERMISSION } from '@/lib/permissions'
import type { ApplicationView, EnvironmentView } from '@/types/tenant'

const ENV_KEY = 'dcc.environmentId'
const APP_KEY = 'dcc.applicationId'
const ENV_CODE_KEY = 'dcc.environmentCode'
const APP_CODE_KEY = 'dcc.applicationCode'

function readStoredNumber(key: string): number | null {
  const raw = localStorage.getItem(key)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

/** 加载环境/应用清单并持有当前选择（持久化到 localStorage）。 */
export function ResourceProvider({ children }: { children: ReactNode }) {
  const { permissions, permissionsLoading } = useSession()
  const canReadTopology = permissions.includes(PERMISSION.topologyRead)
  const [environments, setEnvironments] = useState<EnvironmentView[]>([])
  const [applications, setApplications] = useState<ApplicationView[]>([])
  const [environmentId, setEnvironmentIdState] = useState<number | null>(() => readStoredNumber(ENV_KEY))
  const [applicationId, setApplicationIdState] = useState<number | null>(() => readStoredNumber(APP_KEY))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (permissionsLoading) return
    if (!canReadTopology) {
      // 没有租户拓扑权限（如平台管理员）时不得请求环境/应用，否则每次挂载都会得到 403。
      setEnvironments([])
      setApplications([])
      setEnvironmentIdState(null)
      setApplicationIdState(null)
      setLoading(false)
      setError(null)
      return
    }
    let cancelled = false
    setLoading(true)
    Promise.all([environmentsApi.list(), applicationsApi.list()])
      .then(([envs, apps]) => {
        if (cancelled) return
        setEnvironments(envs)
        setApplications(apps)
        // 未选或已失效时回落到第一项。
        setEnvironmentIdState((prev) =>
          prev != null && envs.some((env) => env.id === prev) ? prev : (envs[0]?.id ?? null),
        )
        setApplicationIdState((prev) =>
          prev != null && apps.some((app) => app.id === prev) ? prev : (apps[0]?.id ?? null),
        )
        const selectedEnvironment = envs.find((env) => env.id === readStoredNumber(ENV_KEY)) ?? envs[0]
        const selectedApplication = apps.find((app) => app.id === readStoredNumber(APP_KEY)) ?? apps[0]
        if (selectedEnvironment) localStorage.setItem(ENV_CODE_KEY, selectedEnvironment.code)
        else localStorage.removeItem(ENV_CODE_KEY)
        if (selectedApplication) localStorage.setItem(APP_CODE_KEY, selectedApplication.code)
        else localStorage.removeItem(APP_CODE_KEY)
        setError(null)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : '加载环境/应用失败')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [canReadTopology, permissionsLoading])

  const setEnvironmentId = useCallback((id: number | null) => {
    setEnvironmentIdState(id)
    const selected = environments.find((environment) => environment.id === id)
    if (id == null) {
      localStorage.removeItem(ENV_KEY)
      localStorage.removeItem(ENV_CODE_KEY)
    } else {
      localStorage.setItem(ENV_KEY, String(id))
      if (selected) localStorage.setItem(ENV_CODE_KEY, selected.code)
    }
  }, [environments])

  const setApplicationId = useCallback((id: number | null) => {
    setApplicationIdState(id)
    const selected = applications.find((application) => application.id === id)
    if (id == null) {
      localStorage.removeItem(APP_KEY)
      localStorage.removeItem(APP_CODE_KEY)
    } else {
      localStorage.setItem(APP_KEY, String(id))
      if (selected) localStorage.setItem(APP_CODE_KEY, selected.code)
    }
  }, [applications])

  const value = useMemo<ResourceContextValue>(
    () => ({
      environments,
      applications,
      environmentId,
      applicationId,
      loading,
      error,
      setEnvironmentId,
      setApplicationId,
    }),
    [environments, applications, environmentId, applicationId, loading, error, setEnvironmentId, setApplicationId],
  )

  return <ResourceContext.Provider value={value}>{children}</ResourceContext.Provider>
}
