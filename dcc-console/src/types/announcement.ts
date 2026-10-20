/** 公告紧急程度（后端 `AnnouncementLevel`），声明顺序即展示优先级。 */
export type AnnouncementLevel = 'MAINTENANCE' | 'WARNING' | 'INFO'

/** `AnnouncementService.AnnouncementView`。`tenantId` 为 null 表示平台公告。 */
export interface AnnouncementView {
  id: number
  tenantId: string | null
  level: AnnouncementLevel
  title: string
  body: string
  enabled: boolean
  /** 可见窗口起点；null 表示立即生效。 */
  startsAt: string | null
  /** 可见窗口终点；null 表示直到手动停用。 */
  endsAt: string | null
  createdAt: string
  updatedAt: string
}

/** `AnnouncementService.CreateCommand`：发布本租户或平台公告（作用域由调用的接口决定）。 */
export interface CreateAnnouncementBody {
  level: AnnouncementLevel
  title: string
  body: string
  startsAt?: string | null
  endsAt?: string | null
}

/** `AnnouncementService.UpdateCommand`：`enabled` 省略时保持原值。 */
export interface UpdateAnnouncementBody extends CreateAnnouncementBody {
  enabled?: boolean | null
}
