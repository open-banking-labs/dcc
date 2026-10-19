/** 后端 `/api/me`：调用者的有效权限点（claim 角色 + 目录授权 + 角色权限包解析后的并集）。 */
export interface MeView {
  subject: string | null
  tenant: string | null
  permissions: string[]
}
