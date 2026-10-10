/** 解码 JWT 的 payload 段（base64url → JSON）。仅用于展示，不校验签名。 */
export function decodeJwtPayload(token?: string): Record<string, unknown> | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>
  } catch {
    return null
  }
}

/** 读取 token 里的 roles claim（后端 `dcc.security.roles-claim`，默认 `roles`）。 */
export function extractRoles(accessToken?: string): string[] {
  const payload = decodeJwtPayload(accessToken)
  const roles = payload?.roles
  if (Array.isArray(roles)) {
    return roles.filter((role): role is string => typeof role === 'string')
  }
  if (typeof roles === 'string') {
    return [roles]
  }
  return []
}
