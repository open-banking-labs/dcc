import { httpDelete, httpGet, httpPost } from '@/lib/apiClient'

/** Spring Data Page 序列化（前端只取用到的字段）。 */
export interface DirectoryPage {
  content: Array<Record<string, unknown>>
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
}

function toParams(params: Record<string, string | number | null | undefined>) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

/** 授权目录（管理员）：成员关系 + 租户内角色指派，gate 着 directory-mode。 */
export const directoryApi = {
  /** 当前操作者自己的成员关系（任意登录用户）。 */
  myMemberships: () => httpGet<MembershipView[]>('/directory/my-memberships'),
  memberships: {
    search: (subject: string | null, tenant: string | null, page: number, size: number) =>
      httpGet<DirectoryPage>('/directory/memberships', {
        params: toParams({ subject, tenant, page, size }),
      }),
    create: (body: { subject: string; tenantCode: string }) =>
      httpPost<MembershipView>('/directory/memberships', body),
    remove: (id: number) => httpDelete(`/directory/memberships/${id}`),
    restore: (id: number) => httpPost<MembershipView>(`/directory/memberships/${id}/restore`),
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
      httpGet<DirectoryPage>('/directory/role-assignments', {
        params: toParams({ subject, tenant, role, scopeType, scopeCode, page, size }),
      }),
    create: (body: { subject: string; tenantCode: string; role: string; scopeType: AssignmentView['scopeType']; scopeCode?: string | null }) =>
      httpPost<AssignmentView>('/directory/role-assignments', body),
    remove: (id: number) => httpDelete(`/directory/role-assignments/${id}`),
    restore: (id: number) => httpPost<AssignmentView>(`/directory/role-assignments/${id}/restore`),
  },
}
