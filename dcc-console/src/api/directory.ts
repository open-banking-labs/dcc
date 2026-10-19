import { httpDelete, httpGet, httpPost } from '@/lib/apiClient'

/** Spring Data Page 序列化（前端只取用到的字段）。 */
export interface DirectoryPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** `TenantMembershipService.MembershipView`。 */
export interface MembershipView {
  id: number
  subject: string
  tenantCode: string
  createdAt: string
  updatedAt: string
}

/** `RoleAssignmentService.AssignmentView`。 */
export interface AssignmentView extends MembershipView {
  role: string
  scopeType: 'TENANT' | 'ENVIRONMENT' | 'APPLICATION'
  scopeCode: string | null
  /** 授予人（双人确认：确认人必须不同）。 */
  grantedBy: string | null
  /** 特权角色（如 DCC_TENANT_ADMIN）需第二人确认，未确认前不生效。 */
  confirmationRequired: boolean
  confirmedBy: string | null
  confirmedAt: string | null
}

function toParams(params: Record<string, string | number | null | undefined>) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

/** 授权目录（管理员）：成员关系 + 租户内角色指派，gate 着 directory-mode。 */
export function createDirectoryApi(platform: boolean) {
  const prefix = platform ? '/directory' : ''
  const membershipBody = (body: { subject: string; tenantCode: string }) =>
    platform ? body : { subject: body.subject }
  return {
  /** 当前操作者自己的成员关系（任意登录用户）。 */
  myMemberships: () => httpGet<MembershipView[]>('/directory/my-memberships'),
  memberships: {
    search: (subject: string | null, tenant: string | null, page: number, size: number) =>
      httpGet<DirectoryPage<MembershipView>>(`${prefix}/memberships`, {
        params: toParams({ subject, tenant: platform ? tenant : null, page, size }),
      }),
    create: (body: { subject: string; tenantCode: string }) =>
      httpPost<MembershipView>(`${prefix}/memberships`, membershipBody(body)),
    remove: (id: number) => httpDelete(`${prefix}/memberships/${id}`),
    restore: (id: number) => httpPost<MembershipView>(`${prefix}/memberships/${id}/restore`),
  },
  assignments: {
    search: (
      subject: string | null,
      tenant: string | null,
      role: string | null,
      scopeType: AssignmentView['scopeType'] | null,
      scopeCode: string | null,
      page: number,
      size: number,
    ) =>
      httpGet<DirectoryPage<AssignmentView>>(`${prefix}/role-assignments`, {
        params: toParams({ subject, tenant: platform ? tenant : null, role, scopeType, scopeCode, page, size }),
      }),
    create: (body: { subject: string; tenantCode: string; role: string; scopeType: AssignmentView['scopeType']; scopeCode?: string | null }) =>
      httpPost<AssignmentView>(`${prefix}/role-assignments`, platform ? body : {
        subject: body.subject, role: body.role, scopeType: body.scopeType, scopeCode: body.scopeCode,
      }),
    remove: (id: number) => httpDelete(`${prefix}/role-assignments/${id}`),
    restore: (id: number) => httpPost<AssignmentView>(`${prefix}/role-assignments/${id}/restore`),
    /** 双人确认：特权角色必须由**另一个**主体确认后才生效。 */
    confirm: (id: number) => httpPost<AssignmentView>(`${prefix}/role-assignments/${id}/confirm`),
  },
}
}

export const directoryApi = createDirectoryApi(true)
export const tenantDirectoryApi = createDirectoryApi(false)
