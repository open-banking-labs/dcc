import {
  AppWindow,
  ArrowRightLeft,
  BookMarked,
  BookText,
  Boxes,
  Building2,
  History,
  Layers,
  LayoutDashboard,
  LayoutTemplate,
  Megaphone,
  Menu,
  Package,
  Plug,
  Radar,
  Table2,
  UserCog,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

import { PERMISSION } from '@/lib/permissions'
import { cn } from '@/lib/utils'

import { useSession } from '@/auth/session'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** 需要的权限点；入口可声明多个候选，命中任一即显示。 */
  authority?: string | string[]
  /** 精确匹配路由（"/" 必须开，否则每条路由下它都算选中）。 */
  end?: boolean
}

interface NavSection {
  group: string
  items: NavItem[]
}

const NAV: NavSection[] = [
  {
    group: '工作台',
    items: [
      // 不需权限点：看板的分区由服务端按权限裁剪，任何已认证用户都有东西可看
      { to: '/', label: '工作台', icon: LayoutDashboard, end: true },
      {
        to: '/announcements',
        label: '公告',
        icon: Megaphone,
        authority: [PERMISSION.announcementWrite, PERMISSION.announcementWritePlatform],
      },
    ],
  },
  {
    group: '数据资产',
    items: [
      { to: '/standards', label: '数标管理', icon: BookText, authority: PERMISSION.artifactRead },
      { to: '/tables', label: '表结构', icon: Table2, authority: PERMISSION.artifactRead },
      { to: '/interfaces', label: '接口', icon: Plug, authority: PERMISSION.artifactRead },
      { to: '/templates', label: '接口模板', icon: LayoutTemplate, authority: PERMISSION.artifactRead },
      { to: '/cases', label: '指导案例', icon: BookMarked, authority: PERMISSION.caseRead },
    ],
  },
  {
    group: '变更与发布',
    items: [
      { to: '/migrations', label: '迁移单', icon: ArrowRightLeft, authority: PERMISSION.artifactRead },
      { to: '/drift', label: '漂移检查', icon: Radar, authority: PERMISSION.artifactRead },
    ],
  },
  {
    group: '发布',
    items: [{ to: '/generation', label: '应用发布', icon: Package, authority: PERMISSION.generationRead }],
  },
  {
    group: '平台管理',
    items: [
      { to: '/tenants', label: '租户', icon: Building2, authority: PERMISSION.tenantAdmit },
      { to: '/environments', label: '环境', icon: Layers, authority: PERMISSION.topologyRead },
      { to: '/applications', label: '应用', icon: AppWindow, authority: PERMISSION.topologyRead },
      { to: '/audit', label: '审计日志', icon: History, authority: PERMISSION.auditBusiness },
      { to: '/platform/audit', label: '平台审计', icon: History, authority: PERMISSION.auditPlatform },
      { to: '/directory', label: '授权目录', icon: UserCog, authority: PERMISSION.directoryRead },
    ],
  },
]

/** 侧栏导航内容，桌面侧栏与移动端抽屉共用。 */
export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const permissions = useSession().permissions
  const visible = NAV.map((section) => ({
    ...section,
    items: section.items.filter((item) =>
      !item.authority
        || (Array.isArray(item.authority) ? item.authority : [item.authority])
          .some((permission) => permissions.includes(permission))),
  })).filter((section) => section.items.length > 0)

  return (
    <nav aria-label="主导航" className="flex-1 space-y-5 overflow-y-auto px-3 py-4">
      {visible.map((section) => (
        <div key={section.group}>
          <div className="mb-1.5 px-2.5 text-[12px] font-semibold leading-4 text-muted-foreground">
            {section.group}
          </div>
          <ul className="space-y-0.5">
            {section.items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'group relative flex h-8 items-center gap-2.5 rounded-md px-2.5 text-[13px] transition-colors',
                      'focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25',
                      isActive
                        ? 'bg-accent font-semibold text-foreground before:absolute before:left-0 before:top-1/2 before:h-4 before:w-[2px] before:-translate-y-1/2 before:rounded-full before:bg-primary'
                        : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-foreground',
                    )
                  }
                >
                  {({ isActive }) => (
                    <>
                      <item.icon
                        aria-hidden
                        className={cn(
                          'h-4 w-4 shrink-0',
                          isActive ? 'text-foreground' : 'text-muted-foreground group-hover:text-foreground',
                        )}
                      />
                      <span className="truncate">{item.label}</span>
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

/** 桌面端固定侧边栏；品牌信息在顶栏，避免重复。 */
export function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 flex-col border-r border-border bg-sidebar lg:flex">
      <SidebarNav />
    </aside>
  )
}

/** 移动端导航抽屉：从顶栏下方滑出，路由变化后自动关闭。 */
export function MobileNav() {
  const [open, setOpen] = useState(false)
  const { pathname } = useLocation()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  const close = () => {
    setOpen(false)
    triggerRef.current?.focus()
  }

  useEffect(() => {
    setOpen(false)
  }, [pathname])

  useEffect(() => {
    if (!open) return
    closeRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label="打开导航"
        aria-expanded={open}
        aria-controls="mobile-navigation"
        onClick={() => setOpen(true)}
        className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-header-muted transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-white/25 lg:hidden"
      >
        <Menu className="h-4 w-4" aria-hidden />
      </button>

      {open && (
        <div className="fixed inset-0 top-14 z-50 lg:hidden">
          <div
            aria-hidden
            className="absolute inset-0 bg-scrim"
            onClick={close}
          />
          <div
            id="mobile-navigation"
            role="dialog"
            aria-modal="true"
            aria-label="主导航"
            className="relative flex h-full w-72 max-w-[85vw] flex-col border-r border-border bg-card shadow-popover"
          >
            <div className="flex h-12 shrink-0 items-center justify-between border-b border-border px-3">
              <span className="flex items-center gap-2 text-[13px] font-semibold text-foreground">
                <Boxes className="h-4 w-4 text-muted-foreground" aria-hidden />
                导航
              </span>
              <button
                ref={closeRef}
                type="button"
                aria-label="关闭导航"
                onClick={close}
                className="grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </div>
            <SidebarNav onNavigate={close} />
          </div>
        </div>
      )}
    </>
  )
}
