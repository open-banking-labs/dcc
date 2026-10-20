import { httpDelete, httpGet, httpPost, httpPut } from '@/lib/apiClient'
import type {
  AnnouncementView,
  CreateAnnouncementBody,
  UpdateAnnouncementBody,
} from '@/types/announcement'

/**
 * 公告接口。作用域由**调用的接口**决定，不由请求体决定：
 * 租户管理员走 `/announcements`（租户取自服务端身份），系统管理员走 `/platform/announcements`
 * （平台级，`tenantId` 恒为 null）。两者在查询层互不可达。
 */
export const announcementsApi = {
  /** 横幅数据：生效中的平台公告 + 本租户公告，服务端已按紧急程度排序。任何已认证用户可读。 */
  visible: () => httpGet<AnnouncementView[]>('/announcements'),

  /** 租户管理列表（含停用与不在窗口内的），需 `announcement.write`。 */
  managed: () => httpGet<AnnouncementView[]>('/announcements/managed'),

  create: (body: CreateAnnouncementBody) => httpPost<AnnouncementView>('/announcements', body),

  update: (id: number, body: UpdateAnnouncementBody) =>
    httpPut<AnnouncementView>(`/announcements/${id}`, body),

  remove: (id: number) => httpDelete(`/announcements/${id}`),
}

/** 平台级公告（`announcement.write.platform`），对所有租户可见。 */
export const platformAnnouncementsApi = {
  list: () => httpGet<AnnouncementView[]>('/platform/announcements'),

  create: (body: CreateAnnouncementBody) =>
    httpPost<AnnouncementView>('/platform/announcements', body),

  update: (id: number, body: UpdateAnnouncementBody) =>
    httpPut<AnnouncementView>(`/platform/announcements/${id}`, body),

  remove: (id: number) => httpDelete(`/platform/announcements/${id}`),
}
