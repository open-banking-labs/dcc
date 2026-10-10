import { createContext, useContext } from 'react'

import type { ApplicationView, EnvironmentView } from '@/types/tenant'

/** 全局资源上下文：所有列表与新增都从这里取 environmentId / applicationId。 */
export interface ResourceContextValue {
  environments: EnvironmentView[]
  applications: ApplicationView[]
  environmentId: number | null
  applicationId: number | null
  loading: boolean
  error: string | null
  setEnvironmentId: (id: number | null) => void
  setApplicationId: (id: number | null) => void
}

export const ResourceContext = createContext<ResourceContextValue | null>(null)

export function useResourceContext(): ResourceContextValue {
  const ctx = useContext(ResourceContext)
  if (!ctx) {
    throw new Error('useResourceContext 必须在 ResourceProvider 内使用')
  }
  return ctx
}
