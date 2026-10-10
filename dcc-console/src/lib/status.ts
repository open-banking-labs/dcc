import { STANDARD_STATUS_LABEL } from './labels'
import type { StandardStatus } from '@/types'

export const STANDARD_STATUSES: StandardStatus[] = [
  'DRAFT',
  'PENDING_REVIEW',
  'PUBLISHED',
  'DEPRECATED',
]

/** 允许的生命周期流转（与后端 StandardStatus.canTransitionTo 对齐）。 */
export const STANDARD_TRANSITIONS: Record<StandardStatus, StandardStatus[]> = {
  DRAFT: ['PENDING_REVIEW'],
  PENDING_REVIEW: ['PUBLISHED', 'DRAFT'],
  PUBLISHED: ['DEPRECATED'],
  DEPRECATED: [],
}

export function standardStatusOptions(): { value: string; label: string }[] {
  return STANDARD_STATUSES.map((status) => ({ value: status, label: STANDARD_STATUS_LABEL[status] }))
}
