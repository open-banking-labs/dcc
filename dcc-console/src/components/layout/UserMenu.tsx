import { ChevronDown, LogOut, Monitor, Moon, Sun, UserRound } from 'lucide-react'
import { useEffect, useState } from 'react'

import { directoryApi, type MembershipView } from '@/api/directory'
import { useSession } from '@/auth/session'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { decodeJwtPayload } from '@/lib/jwt'
import { canIssueLocally, issueDevToken } from '@/lib/devToken'
import { useTheme, type ThemeMode } from '@/theme/ThemeProvider'

/**
 * 顶栏用户菜单：显示身份与角色，提供退出。目录模式下再展示「我的成员关系」，
 * 让操作者知道自己被授权进哪些租户；dev 模式支持一键切换（本页重签 dev Token，
 * OIDC 模式只提示，不代签）。
 */
export function UserMenu() {
  const session = useSession()
  const { mode, setMode } = useTheme()
  const [memberships, setMemberships] = useState<MembershipView[] | null>(null)

  useEffect(() => {
    if (!session.isAuthenticated) return
    let cancelled = false
    directoryApi.myMemberships()
      .then((list) => {
        if (!cancelled) setMemberships(list)
      })
      .catch(() => {
        // 目录端点不可用（未开 directory-mode / 无权限）时静默隐藏该区块
      })
    return () => {
      cancelled = true
    }
  }, [session.isAuthenticated, session.accessToken])

  const switchTo = async (target: string) => {
    if (session.mode !== 'dev' || !session.setDevToken || !session.accessToken) return
    const payload = decodeJwtPayload(session.accessToken)
    const roles = (payload?.roles as string[]) ?? session.roles
    const token = await issueDevToken({
      sub: session.displayName,
      tenant: target,
      roles: roles as string[],
      hours: 8,
    })
    session.setDevToken(token)
    window.location.reload()
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={`账户：${session.displayName}`}
          className="flex items-center gap-2 rounded-md py-1 pl-1 pr-2 text-[13px] text-header-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring"
        >
          <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-accent text-header-muted">
            <UserRound className="h-3.5 w-3.5" aria-hidden />
          </span>
          <span className="hidden max-w-[8rem] truncate sm:inline">{session.displayName}</span>
          <ChevronDown className="h-3.5 w-3.5 text-header-muted" aria-hidden />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56 max-h-[70vh]">
        <DropdownMenuLabel>
          <div className="truncate text-sm">{session.displayName}</div>
          {session.roles.length > 0 && (
            <div className="mt-0.5 text-xs font-normal text-muted-foreground">
              {session.roles.join('、')}
            </div>
          )}
        </DropdownMenuLabel>

        {memberships && memberships.length > 0 && (
          <>
            <DropdownMenuSeparator />
            <div className="px-2 py-1 text-xs text-muted-foreground">我的成员关系</div>
            {memberships.map((membership) => (
              <DropdownMenuItem
                key={membership.id}
                disabled={session.mode !== 'dev' || !canIssueLocally()}
                onSelect={() => void switchTo(membership.tenantCode)}
              >
                <span className="flex w-full items-center justify-between">
                  <span className="truncate font-mono text-xs">{membership.tenantCode}</span>
                  {session.mode === 'dev' && <span className="text-xs text-muted-foreground">切换</span>}
                </span>
              </DropdownMenuItem>
            ))}
          </>
        )}

        <DropdownMenuSeparator />
        <DropdownMenuLabel className="px-2 py-1 text-[12px] font-semibold text-muted-foreground">
          外观
        </DropdownMenuLabel>
        <DropdownMenuRadioGroup
          value={mode}
          onValueChange={(value) => setMode(value as ThemeMode)}
        >
          <DropdownMenuRadioItem value="system">
            <Monitor className="h-4 w-4" aria-hidden />
            跟随系统
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="light">
            <Sun className="h-4 w-4" aria-hidden />
            浅色
          </DropdownMenuRadioItem>
          <DropdownMenuRadioItem value="dark">
            <Moon className="h-4 w-4" aria-hidden />
            深色
          </DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>

        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => session.signout()}>
          <LogOut className="mr-2 h-4 w-4" aria-hidden />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
