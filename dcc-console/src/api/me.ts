import { useEffect, useState } from 'react'

import { httpGet } from '@/lib/apiClient'
import type { MeView } from '@/types/me'

/**
 * 调用者自身的有效权限（服务端解析结果）。
 *
 * UI 的显隐必须以后端为准：JWT 的 `roles` 只是 IdP 的产物，而有效权限是 claim 角色、目录授权
 * 与角色权限包解析后的并集（ADR 0005）。这里只做展示用途，真正的准入始终由后端判定。
 */
export const meApi = {
  get: (token: string) => httpGet<MeView>('/me', { headers: { Authorization: `Bearer ${token}` } }),
}

/**
 * 权限绑定到 token，切换身份时立即清空，避免沿用上个会话的权限。
 */
export function useEffectivePermissions(
  token: string | null | undefined,
): string[] {
  const [result, setResult] = useState<{ token: string; permissions: string[] } | null>(null)

  useEffect(() => {
    if (!token) {
      setResult(null)
      return
    }
    let cancelled = false
    meApi
      .get(token)
      .then((me) => {
        if (!cancelled) setResult({ token, permissions: me.permissions ?? [] })
      })
      .catch(() => {
        if (!cancelled) setResult(null)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  return token && result?.token === token ? result.permissions : []
}
