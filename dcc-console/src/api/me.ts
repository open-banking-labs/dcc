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

export interface EffectivePermissions {
  permissions: string[]
  /** 当前 token 的权限仍在解析中；此时不得据此判定“无权限”。 */
  loading: boolean
}

/**
 * 权限绑定到 token，切换身份时立即清空，避免沿用上个会话的权限。
 *
 * 解析失败按空权限处理并结束 loading：后续请求由后端继续兜底，UI 不无限等待。
 */
export function useEffectivePermissions(
  token: string | null | undefined,
): EffectivePermissions {
  const [result, setResult] = useState<{ token: string; permissions: string[] } | null>(null)
  const [pendingToken, setPendingToken] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setResult(null)
      setPendingToken(null)
      return
    }
    let cancelled = false
    setPendingToken(token)
    meApi
      .get(token)
      .then((me) => {
        if (!cancelled) setResult({ token, permissions: me.permissions ?? [] })
      })
      .catch(() => {
        if (!cancelled) setResult({ token, permissions: [] })
      })
      .finally(() => {
        if (!cancelled) setPendingToken(null)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const current = token && result?.token === token ? result.permissions : null
  return {
    permissions: current ?? [],
    loading: Boolean(token) && (pendingToken === token || current === null),
  }
}
