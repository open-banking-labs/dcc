/** `TenantService.TenantView`。平台级，非租户隔离。 */
export interface TenantView {
  id: number
  code: string
  name: string
  description: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** `TenantEnvironmentService.EnvironmentView`。 */
export interface EnvironmentView {
  id: number
  tenantId: string
  code: string
  name: string
  description: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** `TenantApplicationService.ApplicationView`。 */
export interface ApplicationView {
  id: number
  tenantId: string
  appNo: number
  code: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateTenantBody {
  code: string
  name: string
  description?: string | null
}

export interface UpdateTenantBody {
  name?: string
  description?: string
  enabled?: boolean
}

export interface CreateEnvironmentBody {
  code: string
  name: string
  description?: string | null
  sortOrder?: number
}

export interface UpdateEnvironmentBody {
  name?: string
  description?: string
  sortOrder?: number
}

export interface CreateApplicationBody {
  code: string
  name: string
  description?: string | null
}

export interface UpdateApplicationBody {
  name?: string
  description?: string
}
