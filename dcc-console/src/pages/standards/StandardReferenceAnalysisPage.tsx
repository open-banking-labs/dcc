import { ArrowLeft, ExternalLink } from 'lucide-react'
import { useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState, ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import type { StandardSummaryView } from '@/types'

const CACHE_TTL_MS = 5 * 60_000
const CACHE_MAX_ENTRIES = 50

interface CachedReferences {
  data: string[]
  at: number
}

/**
 * 引用分析子页：左侧按分类分组列出所选数标，点击哪一项才加载哪一项的引用；
 * 结果做 5 分钟、最多 50 条的懒缓存。URL 带 ids 与 from，刷新后仍能回到查询现场。
 */
export function StandardReferenceAnalysisPage() {
  const [searchParams] = useSearchParams()
  const idsParam = searchParams.get('ids') ?? ''
  const fromParam = searchParams.get('from') ?? ''
  const returnTo = fromParam.startsWith('/standards') ? fromParam : '/standards'

  const ids = useMemo(
    () =>
      idsParam
        .split(',')
        .map((value) => Number(value))
        .filter((value) => Number.isFinite(value) && value > 0),
    [idsParam],
  )
  const idKey = ids.join(',')

  const { data, loading, error, reload } = useAsync(
    () => (ids.length > 0 ? standardsApi.summaries(ids) : Promise.resolve([])),
    [idKey],
  )

  const summaries = data ?? []
  const groups = useMemo(() => {
    const grouped = new Map<string, StandardSummaryView[]>()
    for (const summary of summaries) {
      const key = summary.category?.trim() || '未分类'
      grouped.set(key, [...(grouped.get(key) ?? []), summary])
    }
    return Array.from(grouped.entries()).sort(([left], [right]) => left.localeCompare(right, 'zh-CN'))
  }, [summaries])

  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [references, setReferences] = useState<string[] | null>(null)
  const [refLoading, setRefLoading] = useState(false)
  const [refError, setRefError] = useState<string | null>(null)
  const cache = useRef(new Map<number, CachedReferences>())
  const requestSeq = useRef(0)

  const selected = summaries.find((summary) => summary.id === selectedId) ?? null

  const loadReferences = async (id: number) => {
    setSelectedId(id)
    const cached = cache.current.get(id)
    if (cached && Date.now() - cached.at < CACHE_TTL_MS) {
      // Invalidate any request still in flight for a previously clicked standard.
      requestSeq.current += 1
      setReferences(cached.data)
      setRefError(null)
      setRefLoading(false)
      return
    }
    const seq = ++requestSeq.current
    setRefLoading(true)
    setRefError(null)
    setReferences(null)
    try {
      const next = await standardsApi.references(id)
      if (seq !== requestSeq.current) return
      if (cache.current.size >= CACHE_MAX_ENTRIES) {
        const oldest = cache.current.keys().next().value
        if (oldest !== undefined) cache.current.delete(oldest)
      }
      cache.current.set(id, { data: next, at: Date.now() })
      setReferences(next)
    } catch (err) {
      if (seq !== requestSeq.current) return
      setRefError(err instanceof Error ? err.message : '加载引用失败')
    } finally {
      if (seq === requestSeq.current) setRefLoading(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="引用分析"
        description="查看所选数标被哪些表结构、接口或模板引用；点击左侧数标才加载对应详情。"
        actions={
          <Button asChild size="sm" variant="outline">
            <Link to={returnTo}>
              <ArrowLeft className="h-4 w-4" />
              返回查询列表
            </Link>
          </Button>
        }
      />

      {ids.length === 0 ? (
        <EmptyState
          text="未选择数标"
          hint="请返回查询列表勾选数标后再做引用分析。"
          action={
            <Button asChild size="sm" variant="outline">
              <Link to={returnTo}>返回查询列表</Link>
            </Button>
          }
        />
      ) : loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
          <div className="rounded-lg border border-border bg-card p-3">
            {groups.map(([group, items]) => (
              <details key={group} open className="mb-2 last:mb-0">
                <summary className="cursor-pointer select-none rounded-sm px-1 py-1 text-[13px] font-semibold text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/25">
                  {group}
                  <span className="ml-1 font-normal text-muted-foreground">（{items.length}）</span>
                </summary>
                <ul className="mt-1 space-y-0.5">
                  {items.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => void loadReferences(item.id)}
                        className={
                          item.id === selectedId
                            ? 'w-full rounded-md bg-accent px-2 py-1.5 text-left'
                            : 'w-full rounded-md px-2 py-1.5 text-left transition-colors hover:bg-accent/70'
                        }
                      >
                        <span className="font-mono text-xs text-foreground">{item.code}</span>
                        <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                          {item.name ?? '—'}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </details>
            ))}
          </div>

          <div className="rounded-lg border border-border bg-card p-4">
            {!selected ? (
              <EmptyState text="选择一个数标" hint="从左侧选择数标后，这里才会加载它的引用关系。" />
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm text-foreground">{selected.code}</span>
                      <StatusBadge kind="standard" status={selected.status} />
                    </div>
                    <p className="mt-1 text-[13px] text-muted-foreground">{selected.name ?? '—'}</p>
                  </div>
                  <Button asChild size="sm" variant="outline">
                    <Link
                      to={`/standards/${selected.id}?tab=history`}
                      state={{ from: window.location.pathname + window.location.search }}
                    >
                      <ExternalLink className="h-4 w-4" />
                      查看历史版本
                    </Link>
                  </Button>
                </div>

                {refLoading ? (
                  <LoadingState text="加载引用…" />
                ) : refError ? (
                  <ErrorState message={refError} onRetry={() => void loadReferences(selected.id)} />
                ) : (references ?? []).length === 0 ? (
                  <EmptyState text="未被引用" hint="当前没有表结构、接口或模板引用该数标。" />
                ) : (
                  <ul className="space-y-1 text-sm">
                    {(references ?? []).map((reference) => (
                      <li
                        key={reference}
                        className="rounded-md border border-border bg-secondary/40 px-3 py-1.5 font-mono text-xs text-foreground"
                      >
                        {reference}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
