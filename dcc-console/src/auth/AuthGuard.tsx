import { useEffect } from 'react'

import { Outlet, useNavigate } from 'react-router-dom'

import { FullPageMessage } from '@/components/common/FullPageMessage'

import { useSession } from './session'

/** 受保护区路由的守卫：未登录则发起登录（OIDC 跳转 / 开发模式去登录页）。 */
export function AuthGuard() {
  const session = useSession()
  const navigate = useNavigate()

  useEffect(() => {
    if (session.isLoading || session.isAuthenticated || session.error) return
    if (session.mode === 'dev') {
      navigate('/login', { replace: true })
    } else {
      session.signin()
    }
  }, [session, navigate])

  if (session.isLoading) {
    return <FullPageMessage text="正在校验登录状态…" />
  }
  if (session.error) {
    return <FullPageMessage error text={`登录失败：${session.error}`} />
  }
  if (!session.isAuthenticated) {
    return <FullPageMessage text="正在跳转登录…" />
  }
  return <Outlet />
}
