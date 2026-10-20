import { Boxes } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { decodeJwtPayload } from '@/lib/jwt'
import {
  canIssueLocally,
  issueDevToken,
  loadSavedIdentity,
  type DevIdentity,
  ROLE_OPTIONS,
  saveIdentity,
} from '@/lib/devToken'

import { useSession } from './session'

/** 未登录落地页：OIDC 模式显示统一登录入口；开发模式提供身份签发生与 Token 录入。 */
export function LoginPage() {
  const session = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = (location.state as { returnTo?: string } | null)?.returnTo ?? '/'

  useEffect(() => {
    if (session.isAuthenticated) {
      navigate(returnTo, { replace: true })
    }
  }, [session.isAuthenticated, navigate, returnTo])

  if (session.mode === 'oidc') {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-canvas p-6">
        <div className="w-full max-w-sm rounded-lg border border-border bg-card p-6 shadow-popover">
          <Brand />
          <h1 className="mt-4 text-[20px] font-semibold tracking-tight text-foreground">请使用统一身份登录</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            使用公司账号登录后，可访问你被授权的租户与模块。
          </p>
          <Button variant="primary" className="mt-5 w-full" onClick={() => session.signin()}>
            登录
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-canvas p-6">
      <div className="w-full max-w-[30rem] rounded-lg border border-border bg-card shadow-popover">
        <div className="border-b border-border px-6 py-4">
          <Brand />
        </div>
        <div className="px-6 py-5">
          {canIssueLocally() ? (
            <DevIdentityLogin returnTo={returnTo} />
          ) : (
            <DevTokenPaste
              onDone={(token) => {
                session.setDevToken?.(token)
                navigate(returnTo, { replace: true })
              }}
            />
          )}
        </div>
      </div>
    </div>
  )
}

/** 登录页品牌块：与管理端侧栏的标识一致。 */
function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-header-brand text-header-brand-foreground">
        <Boxes className="h-4 w-4" aria-hidden />
      </span>
      <span className="flex min-w-0 flex-col leading-tight">
        <span className="text-[15px] font-semibold text-foreground">DCC 管理端</span>
        <span className="text-[12px] text-muted-foreground">数据契约中心</span>
      </span>
    </div>
  )
}

/** 开发身份表单：选角色/租户一键签发 HS256 JWT，免去手工跑生成脚本再粘贴。 */
function DevIdentityLogin({ returnTo }: { returnTo: string }) {
  const session = useSession()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'identity' | 'token'>('identity')
  const [identity, setIdentity] = useState<DevIdentity>(() => loadSavedIdentity())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const toggleRole = (role: string) => {
    setIdentity((prev) => {
      const has = prev.roles.includes(role)
      return { ...prev, roles: has ? prev.roles.filter((it) => it !== role) : [...prev.roles, role] }
    })
  }

  const submit = async () => {
    setError(null)
    if (!identity.sub.trim()) {
      setError('操作者不能为空')
      return
    }
    if (!identity.tenant.trim()) {
      setError('租户不能为空')
      return
    }
    if (identity.roles.length === 0) {
      setError('至少选择一个角色')
      return
    }
    const normalized: DevIdentity = { ...identity, sub: identity.sub.trim(), tenant: identity.tenant.trim() }
    setSubmitting(true)
    try {
      const token = await issueDevToken(normalized)
      saveIdentity(normalized)
      session.setDevToken?.(token)
      navigate(returnTo, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '签发失败')
    } finally {
      setSubmitting(false)
    }
  }

  const roleSummary = identity.roles.length === ROLE_OPTIONS.length ? '全部角色' : identity.roles.join(' / ')

  return (
    <div className="mt-3">
      {mode === 'identity' ? (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}
        >
          <p className="text-[13px] leading-relaxed text-muted-foreground">
            本地开发登录（未配置 OIDC）。选择身份后本页直接签发开发 Token（与
            <code className="mx-1 rounded-sm bg-secondary px-1 py-0.5 text-[12px] text-foreground">scripts/dev-token.mjs</code>一致的 dev 密钥）。
          </p>
          <div className="space-y-1.5">
            <label htmlFor="dev-sub" className="text-[13px] font-semibold text-foreground">
              操作者
            </label>
            <Input
              id="dev-sub"
              value={identity.sub}
              onChange={(event) => setIdentity((prev) => ({ ...prev, sub: event.target.value }))}
              placeholder="如 zhang.san"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="dev-tenant" className="text-[13px] font-semibold text-foreground">
              租户
            </label>
            <Input
              id="dev-tenant"
              value={identity.tenant}
              onChange={(event) => setIdentity((prev) => ({ ...prev, tenant: event.target.value }))}
              placeholder="default_tenant"
            />
          </div>
          <div className="space-y-1.5">
            <span className="text-[13px] font-semibold text-foreground">角色</span>
            <div className="flex flex-col gap-2 rounded-md border border-border bg-secondary/60 p-3">
              {ROLE_OPTIONS.map((option) => (
                <label key={option.value} className="flex items-center gap-2 text-sm text-foreground">
                  <Checkbox
                    checked={identity.roles.includes(option.value)}
                    onCheckedChange={() => toggleRole(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>
          <div className="space-y-1.5">
            <label htmlFor="dev-hours" className="text-[13px] font-semibold text-foreground">
              有效期
            </label>
            <select
              id="dev-hours"
              value={identity.hours}
              onChange={(event) => setIdentity((prev) => ({ ...prev, hours: Number(event.target.value) }))}
              className="flex h-8 w-full cursor-pointer rounded-md border border-input bg-background px-2.5 text-[13px] text-foreground shadow-sm transition-[border-color,box-shadow] outline-none hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"
            >
              <option value={1}>1 小时</option>
              <option value={8}>8 小时</option>
              <option value={24}>24 小时</option>
              <option value={72}>72 小时</option>
            </select>
          </div>
          {error && <p className="text-[13px] text-destructive">{error}</p>}
          <p className="text-[13px] text-muted-foreground">
            将以 <span className="font-mono text-foreground">{identity.sub}</span> @{' '}
            <span className="font-mono text-foreground">{identity.tenant}</span>（{roleSummary}）登录。
          </p>
          <Button type="submit" variant="primary" className="w-full" disabled={submitting}>
            {submitting ? '签发中…' : '登录'}
          </Button>
        </form>
      ) : (
        <DevTokenPaste
          onDone={(token) => {
            session.setDevToken?.(token)
            navigate(returnTo, { replace: true })
          }}
        />
      )}
      <button
        type="button"
        className="mt-4 w-full rounded-sm text-center text-[12px] text-muted-foreground underline-offset-2 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/25"
        onClick={() => setMode(mode === 'identity' ? 'token' : 'identity')}
      >
        {mode === 'identity' ? '使用已生成的 Token 登录' : '返回身份登录'}
      </button>
    </div>
  )
}

/** 回退：粘贴任意 Token（不走本页签发）。带过期检查与解析预览。 */
function DevTokenPaste({ onDone }: { onDone: (token: string) => void }) {
  const [token, setToken] = useState('')
  const [error, setError] = useState<string | null>(null)

  const parsed = decodeJwtPayload(token.trim())
  const expired =
    parsed && typeof parsed.exp === 'number' ? parsed.exp * 1000 < Date.now() : false

  const submit = () => {
    setError(null)
    const value = token.trim()
    if (!value || !parsed) {
      setError('Token 无效，请粘贴完整的 JWT')
      return
    }
    if (expired) {
      setError(`Token 已过期（${new Date((parsed.exp as number) * 1000).toLocaleString('zh-CN')}），请重新生成`)
      return
    }
    onDone(value)
  }

  return (
    <div className="mt-3">
      <p className="text-[12px] text-muted-foreground">
        粘贴后端开发密钥签发的 JWT（可运行
        <code className="mx-1 font-mono text-foreground">node scripts/dev-token.mjs</code>生成）。
      </p>
      <Textarea
        className="mt-2 min-h-24 font-mono text-[12px]"
        rows={5}
        value={token}
        onChange={(event) => setToken(event.target.value)}
        placeholder="eyJhbGciOiJIUzI1NiIs..."
      />
      {token.trim() && parsed && !expired && (
        <p className="mt-2 text-[12px] text-muted-foreground">
          解析身份：<span className="font-mono text-foreground">{String(parsed.sub ?? '—')}</span> @{' '}
          <span className="font-mono text-foreground">{String(parsed.tenant ?? '—')}</span>
          {Array.isArray(parsed.roles) && (
            <span> · {parsed.roles.length} 个角色</span>
          )}
        </p>
      )}
      {error && <p className="mt-2 text-[12px] text-destructive">{error}</p>}
      <Button type="submit" variant="primary" className="mt-3 w-full" onClick={submit} disabled={!token.trim()}>
        登录
      </Button>
    </div>
  )
}
