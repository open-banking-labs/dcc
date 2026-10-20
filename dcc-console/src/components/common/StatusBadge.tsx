import {
  CheckCircle2,
  Circle,
  CircleDot,
  Clock3,
  GitMerge,
  XCircle,
  type LucideIcon,
} from 'lucide-react'

import { MIGRATION_STATUS_LABEL, STANDARD_STATUS_LABEL } from '@/lib/labels'
import { cn } from '@/lib/utils'
import type { MigrationStatus, StandardStatus } from '@/types'

type Tone = 'neutral' | 'pending' | 'info' | 'success' | 'danger' | 'done'

/** 每个状态只决定一个语义色 + 一个图标；形状（胶囊 + 图标 + 文字）全页统一。 */
const TONE_STYLE: Record<Tone, string> = {
  neutral: 'border-border bg-secondary text-muted-foreground',
  pending: 'border-status-warning/30 bg-status-warning-soft text-status-warning',
  info: 'border-status-info/30 bg-status-info-soft text-status-info',
  success: 'border-status-success/30 bg-status-success-soft text-status-success',
  danger: 'border-status-danger/30 bg-status-danger-soft text-status-danger',
  done: 'border-status-done/30 bg-status-done-soft text-status-done',
}

const TONE_ICON: Record<Tone, LucideIcon> = {
  neutral: Circle,
  pending: Clock3,
  info: CircleDot,
  success: CheckCircle2,
  danger: XCircle,
  done: GitMerge,
}

const STANDARD_TONE: Record<StandardStatus, Tone> = {
  DRAFT: 'neutral',
  PENDING_REVIEW: 'pending',
  PUBLISHED: 'success',
  DEPRECATED: 'neutral',
}

const MIGRATION_TONE: Record<MigrationStatus, Tone> = {
  PENDING_SUBMISSION: 'neutral',
  PENDING_APPROVAL: 'pending',
  APPROVED: 'info',
  MIGRATED: 'done',
  REJECTED: 'danger',
  ROLLED_BACK: 'neutral',
}

type Props =
  | { kind: 'standard'; status: StandardStatus }
  | { kind: 'migration'; status: MigrationStatus }

export function StatusBadge(props: Props) {
  const tone = props.kind === 'standard' ? STANDARD_TONE[props.status] : MIGRATION_TONE[props.status]
  const label =
    props.kind === 'standard'
      ? STANDARD_STATUS_LABEL[props.status]
      : MIGRATION_STATUS_LABEL[props.status]
  const Icon = TONE_ICON[tone]

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-[12px] font-medium leading-4',
        TONE_STYLE[tone],
      )}
    >
      <Icon className="h-3 w-3 shrink-0" aria-hidden />
      {label}
    </span>
  )
}
