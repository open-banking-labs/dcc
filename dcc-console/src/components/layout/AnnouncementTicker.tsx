import { useEffect, useMemo, useState } from 'react'

import { X } from 'lucide-react'

import { announcementsApi } from '@/api/announcements'
import { useAsync } from '@/hooks/useAsync'
import { cn } from '@/lib/utils'
import type { AnnouncementLevel, AnnouncementView } from '@/types/announcement'

/** 轮播间隔：够读完一条短公告，又不至于让「正在维护」被忽略。 */
const ROTATE_MS = 8000

/** 本次会话内被关掉的公告 id（sessionStorage，不持久化——维护公告下次进来还该看见）。 */
const DISMISSED_KEY = 'dcc.dismissedAnnouncements'

const LEVEL_STYLE: Record<AnnouncementLevel, string> = {
  MAINTENANCE: 'border-red-300 bg-red-50 text-red-800',
  WARNING: 'border-amber-300 bg-amber-50 text-amber-800',
  INFO: 'border-blue-200 bg-blue-50 text-blue-800',
}

const LEVEL_LABEL: Record<AnnouncementLevel, string> = {
  MAINTENANCE: '维护',
  WARNING: '注意',
  INFO: '公告',
}

function readDismissed(): number[] {
  try {
    const raw = sessionStorage.getItem(DISMISSED_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((id): id is number => typeof id === 'number') : []
  } catch {
    return []
  }
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

function Banner({ announcement, onDismiss }: { announcement: AnnouncementView; onDismiss: () => void }) {
  return (
    <div
      className={cn(
        'flex items-center gap-2 border-b px-4 py-1.5 text-sm',
        LEVEL_STYLE[announcement.level],
      )}
    >
      <span className="shrink-0 rounded-sm border border-current/30 px-1.5 py-0.5 text-xs leading-none">
        {LEVEL_LABEL[announcement.level]}
      </span>
      <span className="shrink-0 font-medium">{announcement.title}</span>
      <span className="min-w-0 flex-1 truncate">{announcement.body}</span>
      <button
        type="button"
        aria-label="本次会话不再显示这条公告"
        onClick={onDismiss}
        className="shrink-0 rounded p-0.5 hover:bg-black/5"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  )
}

/**
 * 顶部公告条：轮播当前用户可见的公告（平台公告 + 本租户公告，服务端已按紧急程度排好）。
 *
 * 挂在 `AppLayout` 里，所以整个会话只拉取一次；「系统维护」这类公告的关闭只记在
 * sessionStorage —— 关掉的是这一次浏览，不是永久订阅。
 */
export function AnnouncementTicker() {
  const { data } = useAsync<AnnouncementView[]>(() => announcementsApi.visible(), [])
  const [dismissed, setDismissed] = useState<number[]>(readDismissed)
  const [index, setIndex] = useState(0)

  const items = useMemo(
    () => (data ?? []).filter((announcement) => !dismissed.includes(announcement.id)),
    [data, dismissed],
  )

  const staticMode = prefersReducedMotion()

  useEffect(() => {
    if (staticMode || items.length < 2) return
    const timer = setInterval(() => setIndex((current) => (current + 1) % items.length), ROTATE_MS)
    return () => clearInterval(timer)
  }, [items.length, staticMode])

  // 条目变少（关闭或刷新）时，避免停留在越界下标上
  const safeIndex = items.length === 0 ? 0 : index % items.length

  const dismiss = (id: number) => {
    const next = [...dismissed, id]
    setDismissed(next)
    try {
      sessionStorage.setItem(DISMISSED_KEY, JSON.stringify(next))
    } catch {
      // 隐私模式下 sessionStorage 可能不可写：关闭仍然在本次渲染内生效
    }
  }

  if (items.length === 0) return null

  return (
    <div role="status" aria-live="polite">
      {staticMode ? (
        items.map((announcement) => (
          <Banner
            key={announcement.id}
            announcement={announcement}
            onDismiss={() => dismiss(announcement.id)}
          />
        ))
      ) : (
        <Banner
          key={items[safeIndex].id}
          announcement={items[safeIndex]}
          onDismiss={() => dismiss(items[safeIndex].id)}
        />
      )}
    </div>
  )
}
