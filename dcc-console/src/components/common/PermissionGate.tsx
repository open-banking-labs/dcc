import type { ReactNode } from 'react'

import { useSession } from '@/auth/session'

/**
 * 按当前会话的**权限点**显隐内容（ADR 0005）。
 *
 * 传入的是 `PERMISSION.*`，不是角色名：角色只是服务端的权限包，UI 无法也不会自行推导。
 */
export function PermissionGate({
  authority,
  children,
  fallback = null,
}: {
  authority: string
  children: ReactNode
  fallback?: ReactNode
}) {
  const session = useSession()
  const permissions = session.permissions
  return <>{permissions.includes(authority) ? children : fallback}</>
}

/** 命令式判断当前用户是否持有某权限点。 */
export function useHasAuthority(authority: string): boolean {
  const session = useSession()
  return session.permissions.includes(authority)
}
