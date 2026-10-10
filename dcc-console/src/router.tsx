import { lazy, type ComponentType } from 'react'

import { createBrowserRouter, Navigate } from 'react-router-dom'

import { App } from './App'
import { AuthGuard } from './auth/AuthGuard'
import { CallbackPage } from './auth/CallbackPage'
import { LoginPage } from './auth/LoginPage'
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
          { index: true, element: <Navigate to="/standards" replace /> },
          { path: 'standards', element: <StandardsListPage /> },
          { path: 'standards/new', element: <StandardFormPage /> },
          { path: 'standards/:id', element: <StandardDetailPage /> },
          { path: 'standards/:id/edit', element: <StandardFormPage /> },
          { path: 'tables', element: <TableListPage /> },
          { path: 'tables/new', element: <TableFormPage /> },
          { path: 'tables/:id', element: <TableDetailPage /> },
          { path: 'tables/:id/edit', element: <TableFormPage /> },
          { path: 'interfaces', element: <InterfaceListPage /> },
          { path: 'interfaces/new', element: <InterfaceFormPage /> },
          { path: 'interfaces/:id', element: <InterfaceDetailPage /> },
          { path: 'interfaces/:id/edit', element: <InterfaceFormPage /> },
          { path: 'templates', element: <TemplateListPage /> },
          { path: 'templates/new', element: <TemplateFormPage /> },
          { path: 'templates/:id', element: <TemplateDetailPage /> },
          { path: 'templates/:id/edit', element: <TemplateFormPage /> },
          { path: 'migrations', element: <MigrationListPage /> },
          { path: 'migrations/:id', element: <MigrationDetailPage /> },
          { path: 'drift', element: <DriftPage /> },
          { path: 'generation', element: <GenerationPage /> },
          { path: 'tenants', element: <TenantsPage /> },
          { path: 'environments', element: <EnvironmentsPage /> },
          { path: 'applications', element: <ApplicationsPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
