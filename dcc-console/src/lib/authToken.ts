/**
 * 进程内 token 持有者。请求层（apiClient）从这里取 token，OIDC 层（src/auth）
 * 负责写入——避免请求层依赖 React 上下文。
 */
let accessToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

/** OIDC 层注册：收到 401 时触发重新登录。 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export function notifyUnauthorized(): void {
  unauthorizedHandler?.()
}
