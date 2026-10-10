import { useEffect, useState } from 'react'

import { useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { decodeJwtPayload } from '@/lib/jwt'

import { useSession } from './session'

/** 未登录落地页：OIDC 模式显示统一登录入口；开发模式显示 Token 录入。 */
export function LoginPage() {
  const session = useSession()
  const navigate = useNavigate()
  const [token, setToken] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (session.isAuthenticated) {
      navigate('/', { replace: true })
    }
  }, [session.isAuthenticated, navigate])

  if (session.mode === 'oidc') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50">
        <div className="w-80 rounded-md border border-border bg-card p-6">
          <h1 className="text-base font-medium text-foreground">DCC 管理端</h1>
          <p className="mt-1 text-sm text-muted-foreground">请使用统一身份登录</p>
          <Button className="mt-4 w-full" onClick={() => session.signin()}>
            登录
          </Button>
        </div>
      </div>
    )
  }

  const submit = () => {
    const value = token.trim()
    if (!value || !decodeJwtPayload(value)) {
      setError('Token 无效，请粘贴合法的 JWT')
      return
    }
    session.setDevToken?.(value)
    navigate('/', { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50">
      <div className="w-[30rem] rounded-md border border-border bg-card p-6">
        <h1 className="text-base font-medium text-foreground">DCC 管理端 · 本地开发登录</h1>
        <p className="mt-1 text-xs text-muted-foreground">
          未配置 OIDC（VITE_OIDC_ISSUER / VITE_OIDC_CLIENT_ID）。粘贴开发 Token：
          <code className="text-foreground"> node scripts/dev-token.mjs</code>
        </p>
        <Textarea
          className="mt-3 font-mono text-xs"
          rows={5}
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder="eyJhbGciOiJIUzI1NiIs..."
        />
        {error && <p className="mt-2 text-xs text-destructive">{error}</p>}
        <Button className="mt-3 w-full" onClick={submit}>
          登录
        </Button>
      </div>
    </div>
  )
}
