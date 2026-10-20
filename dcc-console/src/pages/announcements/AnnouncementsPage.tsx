import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { FullPageMessage } from '@/components/common/FullPageMessage'
import { ErrorState, LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { Field, TextField } from '@/components/form/fields'
import { FormDialog } from '@/components/form/FormDialog'
import { PageHeader } from '@/components/layout/PageHeader'
import { useAsync } from '@/hooks/useAsync'
import { useSession } from '@/auth/session'
import {
  announcementsApi,
  platformAnnouncementsApi,
} from '@/api/announcements'
import { PERMISSION } from '@/lib/permissions'
import { cn } from '@/lib/utils'
import type { AnnouncementLevel, AnnouncementView } from '@/types/announcement'

const LEVEL_OPTIONS: { value: AnnouncementLevel; label: string }[] = [
  { value: 'MAINTENANCE', label: '维护（红色，最高优先）' },
  { value: 'WARNING', label: '注意（橙色）' },
  { value: 'INFO', label: '普通公告（蓝色）' },
]

const LEVEL_STYLE: Record<AnnouncementLevel, string> = {
  MAINTENANCE: 'border-red-300 bg-red-50 text-red-700',
  WARNING: 'border-amber-300 bg-amber-50 text-amber-700',
  INFO: 'border-blue-200 bg-blue-50 text-blue-700',
}

const LEVEL_LABEL: Record<AnnouncementLevel, string> = {
  MAINTENANCE: '维护',
  WARNING: '注意',
  INFO: '公告',
}

/** ISO → `datetime-local` 的本地时间写法。 */
function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** `datetime-local` 的本地时间写法 → ISO；空串表示"不限制"。 */
function toIso(local: string): string | null {
  return local ? new Date(local).toISOString() : null
}

interface Draft {
  level: AnnouncementLevel
  title: string
  body: string
  startsAt: string
  endsAt: string
  enabled: boolean
}

const EMPTY_DRAFT: Draft = { level: 'INFO', title: '', body: '', startsAt: '', endsAt: '', enabled: true }

function toDraft(announcement: AnnouncementView): Draft {
  return {
    level: announcement.level,
    title: announcement.title,
    body: announcement.body,
    startsAt: toLocalInput(announcement.startsAt),
    endsAt: toLocalInput(announcement.endsAt),
    enabled: announcement.enabled,
  }
}

/**
 * 公告管理。
 *
 * 用哪一套接口由权限点决定，**不由用户选择**：有 `announcement.write.platform` 走平台端点
 * （发布给所有租户），只有 `announcement.write` 走租户端点（只发本租户）。服务端两套端点
 * 在查询层互不可达，所以这里选错接口也越不了权。
 */
export function AnnouncementsPage() {
  const { permissions } = useSession()
  const toast = useToast()
  const platform = permissions.includes(PERMISSION.announcementWritePlatform)
  const tenantWritable = permissions.includes(PERMISSION.announcementWrite)
  const api = platform ? platformAnnouncementsApi : tenantWritable ? announcementsApi : null
  /** 两套接口的「管理列表」不同名（平台 list / 租户 managed），先选出加载函数。 */
  const loadList = platform ? platformAnnouncementsApi.list : announcementsApi.managed

  const { data, loading, error, reload } = useAsync<AnnouncementView[]>(
    () => (api ? loadList() : Promise.resolve([])),
    [platform, tenantWritable],
  )
  const [editing, setEditing] = useState<AnnouncementView | null>(null)
  const [creating, setCreating] = useState(false)
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT)
  const [submitting, setSubmitting] = useState(false)
  const [deleting, setDeleting] = useState<AnnouncementView | null>(null)
  const [removing, setRemoving] = useState(false)

  if (!api) {
    return (
      <div className="p-6">
        <FullPageMessage text="当前账号没有公告发布权限。如需发布公告，请联系系统管理员或租户管理员授权。" />
      </div>
    )
  }

  const openCreate = () => {
    setDraft(EMPTY_DRAFT)
    setCreating(true)
  }

  const openEdit = (announcement: AnnouncementView) => {
    setDraft(toDraft(announcement))
    setEditing(announcement)
  }

  const closeDialog = () => {
    setCreating(false)
    setEditing(null)
  }

  const save = async () => {
    if (!draft.title.trim() || !draft.body.trim()) {
      toast.error('标题与内容不能为空')
      return
    }
    setSubmitting(true)
    try {
      if (editing) {
        await api.update(editing.id, {
          level: draft.level,
          title: draft.title,
          body: draft.body,
          startsAt: toIso(draft.startsAt),
          endsAt: toIso(draft.endsAt),
          enabled: draft.enabled,
        })
        toast.success('公告已更新')
      } else {
        await api.create({
          level: draft.level,
          title: draft.title,
          body: draft.body,
          startsAt: toIso(draft.startsAt),
          endsAt: toIso(draft.endsAt),
        })
        toast.success('公告已发布')
      }
      closeDialog()
      reload()
    } catch (err) {
      toast.error('保存失败', err instanceof Error ? err.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  const toggleEnabled = async (announcement: AnnouncementView) => {
    try {
      await api.update(announcement.id, {
        level: announcement.level,
        title: announcement.title,
        body: announcement.body,
        startsAt: announcement.startsAt,
        endsAt: announcement.endsAt,
        enabled: !announcement.enabled,
      })
      toast.success(announcement.enabled ? '已停用' : '已启用')
      reload()
    } catch (err) {
      toast.error('操作失败', err instanceof Error ? err.message : undefined)
    }
  }

  const confirmDelete = async () => {
    if (!deleting) return
    setRemoving(true)
    try {
      await api.remove(deleting.id)
      toast.success('公告已删除')
      setDeleting(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setRemoving(false)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="公告"
        description={
          platform
            ? '平台公告：所有租户的登录用户都会在顶部看到'
            : '租户公告：仅本租户的登录用户看到（平台公告由系统管理员发布）'
        }
        actions={
          <Button size="sm" onClick={openCreate}>
            新建公告
          </Button>
        }
      />

      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !data || data.length === 0 ? (
        <p className="rounded-md border border-border bg-card px-3 py-12 text-center text-sm text-muted-foreground">
          还没有公告
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-md border border-border bg-card">
          {data.map((announcement) => (
            <li key={announcement.id} className="flex items-start gap-3 px-3 py-2.5">
              <span
                className={cn(
                  'mt-0.5 shrink-0 rounded-sm border px-1.5 py-0.5 text-xs leading-none',
                  LEVEL_STYLE[announcement.level],
                )}
              >
                {LEVEL_LABEL[announcement.level]}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate text-sm font-medium text-foreground">{announcement.title}</span>
                  {!announcement.enabled && (
                    <span className="shrink-0 text-xs text-muted-foreground">已停用</span>
                  )}
                </div>
                <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">{announcement.body}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  生效：
                  {announcement.startsAt ? new Date(announcement.startsAt).toLocaleString() : '立即'}
                  {' — '}
                  {announcement.endsAt ? new Date(announcement.endsAt).toLocaleString() : '手动停用'}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-1.5">
                <Button variant="outline" size="sm" onClick={() => openEdit(announcement)}>
                  编辑
                </Button>
                <Button variant="outline" size="sm" onClick={() => void toggleEnabled(announcement)}>
                  {announcement.enabled ? '停用' : '启用'}
                </Button>
                <Button variant="destructive" size="sm" onClick={() => setDeleting(announcement)}>
                  删除
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <FormDialog
        open={creating || editing !== null}
        onOpenChange={(open) => (open ? undefined : closeDialog())}
        title={editing ? '编辑公告' : '新建公告'}
        description={platform ? '发布给所有租户' : '只发布给本租户'}
        onSubmit={() => void save()}
        submitting={submitting}
      >
        <Field label="级别" htmlFor="announcement-level" required>
          <select
            id="announcement-level"
            value={draft.level}
            onChange={(event) =>
              setDraft({ ...draft, level: event.target.value as AnnouncementLevel })
            }
            className="h-9 w-full rounded-md border border-input bg-background px-2 text-sm"
          >
            {LEVEL_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="标题" htmlFor="announcement-title" required>
          <TextField
            id="announcement-title"
            value={draft.title}
            onChange={(title) => setDraft({ ...draft, title })}
            placeholder="例如：本周六 02:00–04:00 系统维护"
          />
        </Field>
        <Field label="内容" htmlFor="announcement-body" required hint="纯文本，顶部横幅按单行截断显示">
          <Textarea
            id="announcement-body"
            value={draft.body}
            onChange={(event) => setDraft({ ...draft, body: event.target.value })}
            rows={4}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="开始时间" htmlFor="announcement-start" hint="留空表示立即生效">
            <TextField
              id="announcement-start"
              type="datetime-local"
              value={draft.startsAt}
              onChange={(startsAt) => setDraft({ ...draft, startsAt })}
            />
          </Field>
          <Field label="结束时间" htmlFor="announcement-end" hint="留空表示直到手动停用">
            <TextField
              id="announcement-end"
              type="datetime-local"
              value={draft.endsAt}
              onChange={(endsAt) => setDraft({ ...draft, endsAt })}
            />
          </Field>
        </div>
      </FormDialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => (open ? undefined : setDeleting(null))}
        title="删除公告"
        description={`删除后顶部横幅不再显示「${deleting?.title ?? ''}」。这是逻辑删除，可审计追溯。`}
        loading={removing}
        onConfirm={() => void confirmDelete()}
      />
    </div>
  )
}
