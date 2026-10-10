import { useEffect } from 'react'

import { useNavigate } from 'react-router-dom'

import { FullPageMessage } from '@/components/common/FullPageMessage'

import { useSession } from './session'

/** 回调落点：OIDC 换票由库自动完成，这里负责跳回原目标页。 */
export function CallbackPage() {
  const session = useSession()
  const navigate = useNavigate()

  useEffect(() => {
    if (session.isAuthenticated) {
      navigate(session.returnTo || '/', { replace: true })
    }
  }, [session.isAuthenticated, session.returnTo, navigate])

  if (session.error) {
    return <FullPageMessage error text={`登录失败：${session.error}`} />
  }
  return <FullPageMessage text="正在完成登录…" />
}
