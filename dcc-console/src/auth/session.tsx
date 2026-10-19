import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

import { AuthProvider, useAuth } from 'react-oidc-context'

import { useEffectivePermissions } from '@/api/me'
import { decodeJwtPayload, extractRoles } from '@/lib/jwt'
import { oidcConfig, oidcConfigured } from '@/lib/oidc'

/**
 * 统一会话抽象：OIDC（生产，`VITE_OIDC_*` 已配置）或本地开发 Token 模式
 * （未配置 OIDC 时，供联调使用）。UI 只依赖本接口，不直接绑 OIDC 库。
 *
 * `roles` 是 token 里的角色（身份）；`permissions` 是后端 `/api/me` 解析出的**权限点**
 * （claim 角色 + 目录授权 + 角色权限包）。显隐一律用 permissions（ADR 0005）。
 */
export interface Session {
  mode: 'oidc' | 'dev'
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  displayName: string
  roles: string[]
  permissions: string[]
  /** `/api/me` 权限仍在加载；此时不得渲染“无权限”结论。 */
  permissionsLoading: boolean
  accessToken: string | null
  returnTo: string
  signin: () => void
  signout: () => void
  setDevToken?: (token: string | null) => void
}

const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const ctx = useContext(SessionContext)
  if (!ctx) {
    throw new Error('useSession 必须在 SessionProvider 内使用')
  }
  return ctx
}

const DEV_TOKEN_KEY = 'dcc.devToken'

export function SessionProvider({ children }: { children: ReactNode }) {
  if (oidcConfigured) {
    return (
      <AuthProvider {...oidcConfig}>
        <OidcSessionBridge>{children}</OidcSessionBridge>
      </AuthProvider>
    )
  }
  return <DevSessionProvider>{children}</DevSessionProvider>
}

function OidcSessionBridge({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const token = auth.user?.access_token ?? null
  const roles = useMemo(() => extractRoles(token ?? undefined), [token])
  const { permissions, loading: permissionsLoading } = useEffectivePermissions(token)

  const value = useMemo<Session>(() => {
    const profile = auth.user?.profile
    return {
      mode: 'oidc',
      isAuthenticated: auth.isAuthenticated,
      isLoading: auth.isLoading,
      error: auth.error ? auth.error.message : null,
      displayName:
        (profile?.name as string) ||
        (profile?.preferred_username as string) ||
        (profile?.sub as string) ||
        '用户',
      roles,
      permissions,
      permissionsLoading,
      accessToken: token,
      returnTo: (auth.user?.state as { returnTo?: string } | undefined)?.returnTo ?? '/',
      signin: () => void auth.signinRedirect(),
      signout: () => void auth.signoutRedirect(),
    }
  }, [auth, roles, permissions, permissionsLoading, token])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

function DevSessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(DEV_TOKEN_KEY))

  const setDevToken = useCallback((next: string | null) => {
    if (next) localStorage.setItem(DEV_TOKEN_KEY, next)
    else localStorage.removeItem(DEV_TOKEN_KEY)
    setToken(next)
  }, [])

  const roles = useMemo(() => extractRoles(token ?? undefined), [token])
  const { permissions, loading: permissionsLoading } = useEffectivePermissions(token)

  const value = useMemo<Session>(() => {
    const payload = decodeJwtPayload(token ?? undefined)
    return {
      mode: 'dev',
      isAuthenticated: Boolean(token),
      isLoading: false,
      error: null,
      displayName: (payload?.sub as string) ?? '本地开发',
      roles,
      permissions,
      permissionsLoading,
      accessToken: token,
      returnTo: '/',
      signin: () => {},
      signout: () => setDevToken(null),
      setDevToken,
    }
  }, [token, roles, permissions, permissionsLoading, setDevToken])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
