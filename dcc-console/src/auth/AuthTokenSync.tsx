import { useEffect } from 'react'

import { setAccessToken, setUnauthorizedHandler } from '@/lib/authToken'

import { useSession } from './session'

/**
 * 把会话同步进请求层：token 注入 apiClient，401 时触发重新登录。
 * 渲染为 null，挂在 SessionProvider 内即可全局生效。
 */
export function AuthTokenSync() {
  const session = useSession()

  useEffect(() => {
    setAccessToken(session.accessToken)
  }, [session.accessToken])

  useEffect(() => {
    setUnauthorizedHandler(() => session.signin())
    return () => setUnauthorizedHandler(null)
  }, [session])

  return null
}
