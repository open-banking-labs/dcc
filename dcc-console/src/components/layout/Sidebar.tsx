import {
  AppWindow,
  ArrowRightLeft,
  BookMarked,
  BookText,
  Building2,
  History,
  Layers,
  LayoutTemplate,
  Package,
  Plug,
  Radar,
  Table2,
  UserCog,
  type LucideIcon,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { PERMISSION } from '@/lib/permissions'
import { cn } from '@/lib/utils'

import { useSession } from '@/auth/session'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** 需要的权限点；所有入口都必须显式声明，由服务端 /api/me 返回的权限过滤。 */
  authority: string | string[]
}

interface NavSection {
  group: string
  items: NavItem[]
}

const NAV: NavSection[] = [
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
    group: '生成',
    items: [{ to: '/generation', label: '产物生成', icon: Package, authority: PERMISSION.generationRead }],
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

/** 左侧固定侧边栏：纯文字 + 图标，选中项用背景色；按 authority 过滤受限入口。 */
export function Sidebar() {
  const permissions = useSession().permissions
  const visible = NAV.map((section) => ({
    ...section,
    items: section.items.filter((item) =>
      (Array.isArray(item.authority) ? item.authority : [item.authority])
        .some((permission) => permissions.includes(permission))),
  })).filter((section) => section.items.length > 0)
  return (
    <aside className="flex w-56 shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="flex h-12 shrink-0 items-center border-b border-border px-4">
        <span className="text-sm font-semibold text-foreground">DCC 管理端</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-2">
        {visible.map((section) => (
          <div key={section.group} className="mb-3">
            <div className="px-2 py-1 text-xs font-medium text-muted-foreground">{section.group}</div>
            <ul className="space-y-0.5">
              {section.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm',
                        isActive
                          ? 'bg-sidebar-accent font-medium text-foreground'
                          : 'text-sidebar-foreground hover:bg-sidebar-accent/60',
                      )
                    }
                  >
                    <item.icon className="h-4 w-4 shrink-0" />
                    <span className="truncate">{item.label}</span>
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
    </aside>
  )
}
