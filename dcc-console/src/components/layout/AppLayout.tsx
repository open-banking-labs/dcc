import { Suspense } from 'react'

import { Outlet } from 'react-router-dom'

import { LoadingState } from '@/components/feedback/StateViews'

import { AnnouncementTicker } from './AnnouncementTicker'
import { Breadcrumbs } from './Breadcrumbs'
import { EnvAppSwitcher } from './EnvAppSwitcher'
import { Sidebar } from './Sidebar'
import { UserMenu } from './UserMenu'

/** 管理端外壳：左固定侧边栏 + 顶栏（面包屑 / 上下文切换 / 用户）+ 公告条 + 内容区。 */
export function AppLayout() {
  return (
    <div className="flex h-screen bg-neutral-50">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-12 shrink-0 items-center justify-between gap-4 border-b border-border bg-card px-4">
          <Breadcrumbs />
          <div className="flex shrink-0 items-center gap-3">
            <EnvAppSwitcher />
            <UserMenu />
          </div>
        </header>
        <AnnouncementTicker />
        <main className="min-w-0 flex-1 overflow-auto">
          <Suspense fallback={<div className="p-6"><LoadingState /></div>}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  )
}
