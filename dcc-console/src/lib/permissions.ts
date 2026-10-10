/** JWT 角色 → 后端 authority 的默认映射（与 DccSecurityProperties 默认一致）。 */
export const AUTHORITY = {
  admin: 'DCC_ADMIN',
  approver: 'DCC_APPROVER',
  user: 'DCC_USER',
} as const
