import type { ReactNode } from 'react'

import { useSession } from '@/auth/session'

import { FullPageMessage } from './FullPageMessage'

/**
 * 页面级权限守卫：直达 URL 时不发无权限的 API 请求，而是给出明确提示。
 *
 * 权限仍在加载时先等待，避免把「尚未解析」误判成「无权限」。
 */
export function RequirePermission({
  permission,
  children,
}: {
  permission: string | string[]
  children: ReactNode
}) {
  const { permissions, permissionsLoading } = useSession()
  if (permissionsLoading) {
    return <FullPageMessage text="正在加载权限…" />
  }
  const required = Array.isArray(permission) ? permission : [permission]
  if (!required.some((item) => permissions.includes(item))) {
    return <FullPageMessage text="当前账号无权访问该页面。" />
  }
  return <>{children}</>
}
