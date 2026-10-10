import type { ReactNode } from 'react'

import { useSession } from '@/auth/session'

/** 按当前会话的 roles（authority）显隐内容。 */
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
  return <>{session.roles.includes(authority) ? children : fallback}</>
}

/** 命令式判断当前用户是否持有某 authority。 */
export function useHasAuthority(authority: string): boolean {
  return useSession().roles.includes(authority)
}
