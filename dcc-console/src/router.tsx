import { lazy, type ComponentType } from 'react'

import { createBrowserRouter, Navigate } from 'react-router-dom'

import { App } from './App'
import { AuthGuard } from './auth/AuthGuard'
import { CallbackPage } from './auth/CallbackPage'
import { LoginPage } from './auth/LoginPage'
import { useSession } from './auth/session'
import { FullPageMessage } from './components/common/FullPageMessage'
import { RequirePermission } from './components/common/RequirePermission'
import { PERMISSION } from './lib/permissions'
import { NotFoundPage } from './pages/NotFound'

/** 按需加载页面组件（具名导出 → React.lazy），配合 AppLayout 的 Suspense 做代码分割。 */
const lazyPage = <M extends object, K extends keyof M>(loader: () => Promise<M>, name: K) =>
  lazy(async () => ({ default: (await loader())[name] as ComponentType }))

const StandardsListPage = lazyPage(() => import('./pages/standards/StandardsListPage'), 'StandardsListPage')
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

/** 按服务端返回的权限点选择首个可访问入口，避免无业务权限的管理员落到 403 首页。 */
function HomeRedirect() {
  const { permissions } = useSession()
  const target = [
    [PERMISSION.artifactRead, '/standards'],
    [PERMISSION.tenantAdmit, '/tenants'],
    [PERMISSION.topologyRead, '/environments'],
    [PERMISSION.auditBusiness, '/audit'],
    [PERMISSION.auditPlatform, '/platform/audit'],
    [PERMISSION.directoryRead, '/directory'],
    [PERMISSION.caseRead, '/cases'],
    [PERMISSION.generationRead, '/generation'],
  ].find(([permission]) => permissions.includes(permission))?.[1]
  return <Navigate to={target ?? '/no-access'} replace />
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
          { index: true, element: <HomeRedirect /> },
          { path: 'no-access', element: <FullPageMessage text="当前账号没有任何可访问的菜单，请联系管理员授权。" /> },
          { path: 'standards', element: <RequirePermission permission={PERMISSION.artifactRead}><StandardsListPage /></RequirePermission> },
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
