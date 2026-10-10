import {
  AppWindow,
  ArrowRightLeft,
  BookText,
  Building2,
  Layers,
  LayoutTemplate,
  Package,
  Plug,
  Radar,
  Table2,
  type LucideIcon,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

interface NavSection {
  group: string
  items: NavItem[]
}

const NAV: NavSection[] = [
  {
    group: '数据资产',
    items: [
      { to: '/standards', label: '数标管理', icon: BookText },
      { to: '/tables', label: '表结构', icon: Table2 },
      { to: '/interfaces', label: '接口', icon: Plug },
      { to: '/templates', label: '接口模板', icon: LayoutTemplate },
    ],
  },
  {
    group: '变更与发布',
    items: [
      { to: '/migrations', label: '迁移单', icon: ArrowRightLeft },
      { to: '/drift', label: '漂移检查', icon: Radar },
    ],
  },
  {
    group: '生成',
    items: [{ to: '/generation', label: '产物生成', icon: Package }],
  },
  {
    group: '平台管理',
    items: [
      { to: '/tenants', label: '租户', icon: Building2 },
      { to: '/environments', label: '环境', icon: Layers },
      { to: '/applications', label: '应用', icon: AppWindow },
    ],
  },
]

/** 左侧固定侧边栏：纯文字 + 图标，选中项用背景色。 */
export function Sidebar() {
  return (
    <aside className="flex w-56 shrink-0 flex-col border-r border-border bg-sidebar">
      <div className="flex h-12 shrink-0 items-center border-b border-border px-4">
        <span className="text-sm font-semibold text-foreground">DCC 管理端</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-2">
        {NAV.map((section) => (
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
