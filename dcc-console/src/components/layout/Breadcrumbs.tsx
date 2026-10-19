import { ChevronRight } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

const LABELS: Record<string, string> = {
  standards: '数标管理',
  tables: '表结构',
  interfaces: '接口',
  templates: '接口模板',
  migrations: '迁移单',
  drift: '漂移检查',
  generation: '产物生成',
  tenants: '租户',
  environments: '环境',
  applications: '应用',
  cases: '指导案例',
  directory: '授权目录',
  audit: '审计日志',
  platform: '平台',
  new: '新建',
  edit: '编辑',
}

/** 由当前路径生成面包屑。 */
export function Breadcrumbs() {
  const { pathname } = useLocation()
  const segments = pathname.split('/').filter(Boolean)

  const crumbs = segments.map((segment, index) => {
    const to = '/' + segments.slice(0, index + 1).join('/')
    const label = LABELS[segment] ?? (/^\d+$/.test(segment) ? `#${segment}` : segment)
    return { to, label, isLast: index === segments.length - 1 }
  })

  return (
    <nav className="flex min-w-0 items-center gap-1 text-sm">
      <Link to="/" className="shrink-0 text-muted-foreground hover:text-foreground">
        首页
      </Link>
      {crumbs.map((crumb) => (
        <span key={crumb.to} className="flex min-w-0 items-center gap-1">
          <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          {crumb.isLast || crumb.to === '/platform' ? (
            <span className="truncate text-foreground">{crumb.label}</span>
          ) : (
            <Link to={crumb.to} className="truncate text-muted-foreground hover:text-foreground">
              {crumb.label}
            </Link>
          )}
        </span>
      ))}
    </nav>
  )
}
