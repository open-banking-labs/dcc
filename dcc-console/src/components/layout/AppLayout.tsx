import { Boxes } from 'lucide-react'
import { Suspense } from 'react'

import { Link, Outlet } from 'react-router-dom'

import { LoadingState } from '@/components/feedback/StateViews'

import { AnnouncementTicker } from './AnnouncementTicker'
import { Breadcrumbs } from './Breadcrumbs'
import { EnvAppSwitcher } from './EnvAppSwitcher'
import { MobileNav, Sidebar } from './Sidebar'
import { UserMenu } from './UserMenu'

/**
 * 管理端外壳：GitHub 式深色全局顶栏 + 左侧导航 + 公告条 + 内容区。
 * 顶栏全宽，内容区独立滚动；移动端导航收进顶栏左侧的抽屉。
 */
export function AppLayout() {
  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-canvas">
      <header className="flex h-14 shrink-0 items-center gap-2.5 bg-header px-3 text-header-foreground dark:border-b dark:border-header-border sm:gap-3 sm:px-4">
        <MobileNav />
        <Link
          to="/"
          aria-label="DCC 管理端首页"
          className="flex shrink-0 items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-white/25"
        >
          <span className="grid h-8 w-8 place-items-center rounded-md bg-white text-header dark:bg-white/10 dark:text-white">
            <Boxes className="h-4 w-4" aria-hidden />
          </span>
          <span className="hidden min-w-0 flex-col leading-tight sm:flex">
            <span className="truncate text-[14px] font-semibold text-white">DCC 管理端</span>
            <span className="truncate text-[12px] text-header-muted">数据契约中心</span>
          </span>
        </Link>
        <span aria-hidden className="hidden h-5 w-px shrink-0 bg-white/15 md:block" />
        <div className="hidden min-w-0 flex-1 md:block">
          <Breadcrumbs />
        </div>
        <div className="ml-auto flex min-w-0 shrink-0 items-center gap-2">
          <EnvAppSwitcher />
          <span aria-hidden className="hidden h-5 w-px bg-white/15 sm:block" />
          <UserMenu />
        </div>
      </header>
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <AnnouncementTicker />
          <main className="min-w-0 flex-1 overflow-auto">
            <Suspense
              fallback={
                <div className="p-6">
                  <LoadingState />
                </div>
              }
            >
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
    </div>
  )
}
