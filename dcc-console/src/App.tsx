import { AppLayout } from './components/layout/AppLayout'
import { ResourceProvider } from './components/layout/ResourceProvider'
import { ToastProvider } from './components/feedback/Toast'

/** 受保护区外壳：提供全局环境/应用上下文与 Toast，再渲染布局。 */
export function App() {
  return (
    <ResourceProvider>
      <ToastProvider>
        <AppLayout />
      </ToastProvider>
    </ResourceProvider>
  )
}
