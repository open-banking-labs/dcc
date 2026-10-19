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
  get: () => httpGet<MeView>('/me'),
}

/**
 * 取当前会话的有效权限点；请求未返回前先用 claim 角色兜底，失败时保持兜底值，
 * 避免界面在加载瞬间闪回全部按钮。
 */
export function useEffectivePermissions(
  token: string | null | undefined,
  fallback: string[],
): string[] {
  const [permissions, setPermissions] = useState<string[]>(fallback)

  useEffect(() => {
    if (!token) {
      setPermissions(fallback)
      return
    }
    let cancelled = false
    meApi
      .get()
      .then((me) => {
        if (!cancelled) setPermissions(me.permissions ?? fallback)
      })
      .catch(() => {
        if (!cancelled) setPermissions(fallback)
      })
    return () => {
      cancelled = true
    }
    // fallback 是派生值，随 token 变化；不作为依赖，避免每次渲染重复请求
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return permissions
}
