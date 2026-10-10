import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { applicationsApi, environmentsApi } from '@/api/tenancy'
import { ResourceContext, type ResourceContextValue } from '@/hooks/useResourceContext'
import type { ApplicationView, EnvironmentView } from '@/types/tenant'

const ENV_KEY = 'dcc.environmentId'
const APP_KEY = 'dcc.applicationId'

function readStoredNumber(key: string): number | null {
  const raw = localStorage.getItem(key)
  if (!raw) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

/** 加载环境/应用清单并持有当前选择（持久化到 localStorage）。 */
export function ResourceProvider({ children }: { children: ReactNode }) {
  const [environments, setEnvironments] = useState<EnvironmentView[]>([])
  const [applications, setApplications] = useState<ApplicationView[]>([])
  const [environmentId, setEnvironmentIdState] = useState<number | null>(() => readStoredNumber(ENV_KEY))
  const [applicationId, setApplicationIdState] = useState<number | null>(() => readStoredNumber(APP_KEY))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
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
  }, [])

  const setEnvironmentId = useCallback((id: number | null) => {
    setEnvironmentIdState(id)
    if (id == null) localStorage.removeItem(ENV_KEY)
    else localStorage.setItem(ENV_KEY, String(id))
  }, [])

  const setApplicationId = useCallback((id: number | null) => {
    setApplicationIdState(id)
    if (id == null) localStorage.removeItem(APP_KEY)
    else localStorage.setItem(APP_KEY, String(id))
  }, [])

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
