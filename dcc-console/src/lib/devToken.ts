import { AUTHORITY } from './permissions'

/**
 * 本地开发身份签发：与 scripts/dev-token.mjs 同构，在浏览器里用 WebCrypto 签发
 * HS256 JWT（对应后端 DCC_JWT_SECRET 开发默认值）。生产环境后端走 JWKS/OIDC，
 * 该能力只应存在于未配置 OIDC 的联调场景。
 */

const DEV_IDENTITY_KEY = 'dcc.devIdentity'

/** 与 dev-token.mjs / application.yml 的 dev 默认密钥保持一致；可用 VITE_DEV_JWT_SECRET 覆盖。 */
export const DEV_JWT_SECRET: string = import.meta.env.VITE_DEV_JWT_SECRET ?? 'dev-only-change-me-please-use-32-bytes'

export interface DevIdentity {
  /** JWT sub：操作者标识（审计的 actor）。 */
  sub: string
  /** JWT tenant：所属租户。 */
  tenant: string
  /** JWT roles：映射为后端 authority。 */
  roles: string[]
  /** 有效期小时数。 */
  hours: number
}

export const DEFAULT_IDENTITY: DevIdentity = {
  sub: 'local-dev',
  tenant: 'default_tenant',
  roles: [AUTHORITY.user, AUTHORITY.admin, AUTHORITY.approver],
  hours: 8,
}

export const ROLE_OPTIONS = [
  { value: AUTHORITY.admin, label: '管理员 (DCC_ADMIN)' },
  { value: AUTHORITY.approver, label: '审批人 (DCC_APPROVER)' },
  { value: AUTHORITY.user, label: '普通用户 (DCC_USER)' },
]

/** 上次登录的身份（预填表单）；损坏则忽略。 */
export function loadSavedIdentity(): DevIdentity {
  try {
    const raw = localStorage.getItem(DEV_IDENTITY_KEY)
    if (!raw) return DEFAULT_IDENTITY
    const saved = JSON.parse(raw) as Partial<DevIdentity>
    return {
      sub: saved.sub ?? DEFAULT_IDENTITY.sub,
      tenant: saved.tenant ?? DEFAULT_IDENTITY.tenant,
      roles: Array.isArray(saved.roles) && saved.roles.length > 0 ? saved.roles : DEFAULT_IDENTITY.roles,
      hours: saved.hours ?? DEFAULT_IDENTITY.hours,
    }
  } catch {
    return DEFAULT_IDENTITY
  }
}

export function saveIdentity(identity: DevIdentity): void {
  try {
    localStorage.setItem(DEV_IDENTITY_KEY, JSON.stringify(identity))
  } catch {
    // 存储不可用时只影响下次预填，不阻断登录。
  }
}

/** 当前环境能否本地签发（需 secure context，如 localhost / HTTPS）。 */
export function canIssueLocally(): boolean {
  return typeof crypto !== 'undefined' && typeof crypto.subtle !== 'undefined'
}

function base64urlBytes(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64urlText(text: string): string {
  return base64urlBytes(new TextEncoder().encode(text))
}

/** 以开发密钥签发 HS256 JWT（8 小时有效期上限与后端无关，仅为习惯约定）。 */
export async function issueDevToken(identity: DevIdentity): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header = { alg: 'HS256', typ: 'JWT' }
  const payload = {
    sub: identity.sub,
    tenant: identity.tenant,
    roles: identity.roles,
    iat: now,
    exp: now + identity.hours * 60 * 60,
  }
  const unsigned = `${base64urlText(JSON.stringify(header))}.${base64urlText(JSON.stringify(payload))}`
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(DEV_JWT_SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(unsigned))
  return `${unsigned}.${base64urlBytes(new Uint8Array(signature))}`
}
