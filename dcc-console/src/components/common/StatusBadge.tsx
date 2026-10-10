import { MIGRATION_STATUS_LABEL, STANDARD_STATUS_LABEL } from '@/lib/labels'
import { cn } from '@/lib/utils'
import type { MigrationStatus, StandardStatus } from '@/types'

const STANDARD_STYLE: Record<StandardStatus, string> = {
  DRAFT: 'border-neutral-300 bg-neutral-100 text-neutral-600',
  PENDING_REVIEW: 'border-amber-300 bg-amber-50 text-amber-700',
  PUBLISHED: 'border-green-300 bg-green-50 text-green-700',
  DEPRECATED: 'border-neutral-300 bg-neutral-100 text-neutral-400',
}

const MIGRATION_STYLE: Record<MigrationStatus, string> = {
  PENDING_SUBMISSION: 'border-neutral-300 bg-neutral-100 text-neutral-600',
  PENDING_APPROVAL: 'border-amber-300 bg-amber-50 text-amber-700',
  APPROVED: 'border-blue-300 bg-blue-50 text-blue-700',
  MIGRATED: 'border-green-300 bg-green-50 text-green-700',
  REJECTED: 'border-red-300 bg-red-50 text-red-700',
  ROLLED_BACK: 'border-neutral-300 bg-neutral-100 text-neutral-500',
}

type Props =
  | { kind: 'standard'; status: StandardStatus }
  | { kind: 'migration'; status: MigrationStatus }

export function StatusBadge(props: Props) {
  const className =
    props.kind === 'standard' ? STANDARD_STYLE[props.status] : MIGRATION_STYLE[props.status]
  const label =
    props.kind === 'standard'
      ? STANDARD_STATUS_LABEL[props.status]
      : MIGRATION_STATUS_LABEL[props.status]

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-sm border px-1.5 py-0.5 text-xs leading-none',
        className,
      )}
    >
      {label}
    </span>
  )
}
