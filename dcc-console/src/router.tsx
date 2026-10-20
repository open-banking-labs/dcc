import { lazy, type ComponentType } from 'react'

import { createBrowserRouter, Navigate } from 'react-router-dom'

import { App } from './App'
import { AuthGuard } from './auth/AuthGuard'
import { CallbackPage } from './auth/CallbackPage'
import { LoginPage } from './auth/LoginPage'
import { useSession } from './auth/session'
import { FullPageMessage } from './components/common/FullPageMessage'
import { RequirePermission } from './components/common/RequirePermission'
import { LoadingState } from './components/feedback/StateViews'
import { PERMISSION } from './lib/permissions'
import { NotFoundPage } from './pages/NotFound'

/** 按需加载页面组件（具名导出 → React.lazy），配合 AppLayout 的 Suspense 做代码分割。 */
const lazyPage = <M extends object, K extends keyof M>(loader: () => Promise<M>, name: K) =>
  lazy(async () => ({ default: (await loader())[name] as ComponentType }))

const DashboardPage = lazyPage(() => import('./pages/dashboard/DashboardPage'), 'DashboardPage')
const AnnouncementsPage = lazyPage(() => import('./pages/announcements/AnnouncementsPage'), 'AnnouncementsPage')

const StandardsQueryPage = lazyPage(() => import('./pages/standards/StandardsQueryPage'), 'StandardsQueryPage')
const StandardsMaintenancePage = lazyPage(
  () => import('./pages/standards/StandardsMaintenancePage'),
  'StandardsMaintenancePage',
)
const StandardReferenceAnalysisPage = lazyPage(
  () => import('./pages/standards/StandardReferenceAnalysisPage'),
  'StandardReferenceAnalysisPage',
)
const StandardFormPage = lazyPage(() => import('./pages/standards/StandardFormPage'), 'StandardFormPage')
const StandardDetailPage = lazyPage(() => import('./pages/standards/StandardDetailPage'), 'StandardDetailPage')

const TableListPage = lazyPage(() => import('./pages/tables/TableListPage'), 'TableListPage')
const TableFormPage = lazyPage(() => import('./pages/tables/TableFormPage'), 'TableFormPage')
const TableDetailPage = lazyPage(() => import('./pages/tables/TableDetailPage'), 'TableDetailPage')

const InterfaceListPage = lazyPage(() => import('./pages/interfaces/InterfaceListPage'), 'InterfaceListPage')
const InterfaceFormPage = lazyPage(() => import('./pages/interfaces/InterfaceFormPage'), 'InterfaceFormPage')
const InterfaceDetailPage = lazyPage(() => import('./pages/interfaces/InterfaceDetailPage'), 'InterfaceDetailPage')

const TemplateListPage = lazyPage(() => import('./pages/templates/TemplateListPage'), 'TemplateListPage')
const TemplateFormPage = lazyPage(() => import('./pages/templates/TemplateFormPage'), 'TemplateFormPage')
const TemplateDetailPage = lazyPage(() => import('./pages/templates/TemplateDetailPage'), 'TemplateDetailPage')

const MigrationListPage = lazyPage(() => import('./pages/migrations/MigrationListPage'), 'MigrationListPage')
const MigrationDetailPage = lazyPage(() => import('./pages/migrations/MigrationDetailPage'), 'MigrationDetailPage')

const DriftPage = lazyPage(() => import('./pages/drift/DriftPage'), 'DriftPage')
const GenerationPage = lazyPage(() => import('./pages/generation/GenerationPage'), 'GenerationPage')
const TenantsPage = lazyPage(() => import('./pages/tenants/TenantsPage'), 'TenantsPage')
const EnvironmentsPage = lazyPage(() => import('./pages/environments/EnvironmentsPage'), 'EnvironmentsPage')
const ApplicationsPage = lazyPage(() => import('./pages/applications/ApplicationsPage'), 'ApplicationsPage')
const AuditPage = lazyPage(() => import('./pages/audit/AuditPage'), 'AuditPage')
const CasesPage = lazyPage(() => import('./pages/cases/CasesPage'), 'CasesPage')
const DirectoryPage = lazyPage(() => import('./pages/directory/DirectoryPage'), 'DirectoryPage')
/**
 * 登录后的首页：工作台看板。
 *
 * 聚合端点对任何已认证调用方开放（各 section 由服务端按权限点裁剪），所以这里只需挡掉
 * 「一个权限点都没有」的账号：它们看到的是空看板，不如直接说清楚去找管理员。
 */
function HomePage() {
  const { permissions, permissionsLoading } = useSession()
  if (permissionsLoading) {
    return <div className="p-6"><LoadingState /></div>
  }
  if (permissions.length === 0) {
    return <Navigate to="/no-access" replace />
  }
  return <DashboardPage />
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/auth/callback', element: <CallbackPage /> },
  {
    path: '/',
    element: <AuthGuard />,
    children: [
      {
        element: <App />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'no-access', element: <FullPageMessage text="当前账号没有任何可访问的菜单，请联系管理员授权。" /> },
          {
            path: 'announcements',
            element: (
              <RequirePermission
                permission={[PERMISSION.announcementWrite, PERMISSION.announcementWritePlatform]}
              >
                <AnnouncementsPage />
              </RequirePermission>
            ),
          },
          { path: 'standards', element: <RequirePermission permission={PERMISSION.artifactRead}><StandardsQueryPage /></RequirePermission> },
          { path: 'standards/maintenance', element: <RequirePermission permission={PERMISSION.artifactRead}><StandardsMaintenancePage /></RequirePermission> },
          { path: 'standards/reference-analysis', element: <RequirePermission permission={PERMISSION.artifactRead}><StandardReferenceAnalysisPage /></RequirePermission> },
          { path: 'standards/new', element: <RequirePermission permission={PERMISSION.artifactWrite}><StandardFormPage /></RequirePermission> },
          { path: 'standards/:id', element: <RequirePermission permission={PERMISSION.artifactRead}><StandardDetailPage /></RequirePermission> },
          { path: 'standards/:id/edit', element: <RequirePermission permission={PERMISSION.artifactWrite}><StandardFormPage /></RequirePermission> },
          { path: 'tables', element: <RequirePermission permission={PERMISSION.artifactRead}><TableListPage /></RequirePermission> },
          { path: 'tables/new', element: <RequirePermission permission={PERMISSION.artifactWrite}><TableFormPage /></RequirePermission> },
          { path: 'tables/:id', element: <RequirePermission permission={PERMISSION.artifactRead}><TableDetailPage /></RequirePermission> },
          { path: 'tables/:id/edit', element: <RequirePermission permission={PERMISSION.artifactWrite}><TableFormPage /></RequirePermission> },
          { path: 'interfaces', element: <RequirePermission permission={PERMISSION.artifactRead}><InterfaceListPage /></RequirePermission> },
          { path: 'interfaces/new', element: <RequirePermission permission={PERMISSION.artifactWrite}><InterfaceFormPage /></RequirePermission> },
          { path: 'interfaces/:id', element: <RequirePermission permission={PERMISSION.artifactRead}><InterfaceDetailPage /></RequirePermission> },
          { path: 'interfaces/:id/edit', element: <RequirePermission permission={PERMISSION.artifactWrite}><InterfaceFormPage /></RequirePermission> },
          { path: 'templates', element: <RequirePermission permission={PERMISSION.artifactRead}><TemplateListPage /></RequirePermission> },
          { path: 'templates/new', element: <RequirePermission permission={PERMISSION.artifactWrite}><TemplateFormPage /></RequirePermission> },
          { path: 'templates/:id', element: <RequirePermission permission={PERMISSION.artifactRead}><TemplateDetailPage /></RequirePermission> },
          { path: 'templates/:id/edit', element: <RequirePermission permission={PERMISSION.artifactWrite}><TemplateFormPage /></RequirePermission> },
          { path: 'migrations', element: <RequirePermission permission={PERMISSION.artifactRead}><MigrationListPage /></RequirePermission> },
          { path: 'migrations/:id', element: <RequirePermission permission={PERMISSION.artifactRead}><MigrationDetailPage /></RequirePermission> },
          { path: 'drift', element: <RequirePermission permission={PERMISSION.artifactRead}><DriftPage /></RequirePermission> },
          { path: 'generation', element: <RequirePermission permission={PERMISSION.generationRead}><GenerationPage /></RequirePermission> },
          { path: 'tenants', element: <RequirePermission permission={PERMISSION.tenantAdmit}><TenantsPage /></RequirePermission> },
          { path: 'environments', element: <RequirePermission permission={PERMISSION.topologyRead}><EnvironmentsPage /></RequirePermission> },
          { path: 'applications', element: <RequirePermission permission={PERMISSION.topologyRead}><ApplicationsPage /></RequirePermission> },
          { path: 'cases', element: <RequirePermission permission={PERMISSION.caseRead}><CasesPage /></RequirePermission> },
          { path: 'audit', element: <RequirePermission permission={PERMISSION.auditBusiness}><AuditPage /></RequirePermission> },
          { path: 'platform/audit', element: <RequirePermission permission={PERMISSION.auditPlatform}><AuditPage /></RequirePermission> },
          { path: 'directory', element: <RequirePermission permission={PERMISSION.directoryRead}><DirectoryPage /></RequirePermission> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
