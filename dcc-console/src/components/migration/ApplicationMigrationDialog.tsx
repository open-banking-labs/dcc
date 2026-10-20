import { useState } from 'react'

import { migrationsApi } from '@/api/migrations'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, TextField, SelectField } from '@/components/form/fields'
import { Checkbox } from '@/components/ui/checkbox'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { ArtifactType } from '@/types'

const ALL_TYPES: ArtifactType[] = [
  'DATA_STANDARD',
  'TABLE_STRUCTURE',
  'INTERFACE',
  'INTERFACE_TEMPLATE',
]

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 目标应用；已在应用页固定时可传入，产物页可传入其 applicationId。 */
  applicationId: number | null
  /** 是否允许选择产物类型子集（默认全部四类）。 */
  allowTypeFilter?: boolean
  onCreated?: () => void
}

/**
 * 以应用为单位发起迁移的弹窗：选择源/目标环境，为该应用在源环境下的产物各建一张迁移单。
 * 取代旧的"手填产物 ID"新建方式。
 */
export function ApplicationMigrationDialog({
  open,
  onOpenChange,
  applicationId,
  allowTypeFilter = true,
  onCreated,
}: Props) {
  const toast = useToast()
  const { environments } = useResourceContext()

  const envOptions = environments.map((env) => ({ value: String(env.id), label: env.name }))
  const [sourceEnv, setSourceEnv] = useState('')
  const [targetEnv, setTargetEnv] = useState('')
  const [types, setTypes] = useState<ArtifactType[]>(ALL_TYPES)
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const reset = () => {
    setSourceEnv('')
    setTargetEnv('')
    setTypes(ALL_TYPES)
    setNote('')
    setErrors({})
  }

  const toggleType = (type: ArtifactType) => {
    setTypes((prev) => (prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]))
  }

  const submit = async () => {
    const next: Record<string, string> = {}
    if (applicationId == null) next.applicationId = '缺少应用'
    if (!sourceEnv) next.sourceEnv = '请选择源环境'
    if (!targetEnv) next.targetEnv = '请选择目标环境'
    if (sourceEnv && sourceEnv === targetEnv) next.targetEnv = '源环境与目标环境必须不同'
    if (allowTypeFilter && types.length === 0) next.types = '至少选择一类产物'
    setErrors(next)
    if (Object.keys(next).length > 0 || applicationId == null) return

    setSubmitting(true)
    try {
      const result = await migrationsApi.createApplication({
        sourceEnvironmentId: Number(sourceEnv),
        targetEnvironmentId: Number(targetEnv),
        applicationId,
        artifactTypes: allowTypeFilter ? types : null,
        note: note.trim() || null,
      })
      toast.success(
        '已发起迁移',
        `新建 ${result.created.length} 张，跳过 ${result.skipped.length} 张`,
      )
      reset()
      onOpenChange(false)
      onCreated?.()
    } catch (err) {
      toast.error('发起失败', err instanceof Error ? err.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset()
        onOpenChange(next)
      }}
      title="发起应用迁移"
      description="为该应用在源环境下的产物各建一张迁移单（目标已同版本会自动跳过）。"
      onSubmit={() => void submit()}
      submitting={submitting}
      submitText="发起"
    >
      {errors.applicationId && <p className="text-xs text-destructive">{errors.applicationId}</p>}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label="源环境" htmlFor="m-src" required error={errors.sourceEnv}>
          <SelectField
            id="m-src"
            value={sourceEnv}
            onChange={setSourceEnv}
            options={envOptions}
            placeholder="选择"
          />
        </Field>
        <Field label="目标环境" htmlFor="m-dst" required error={errors.targetEnv}>
          <SelectField
            id="m-dst"
            value={targetEnv}
            onChange={setTargetEnv}
            options={envOptions}
            placeholder="选择"
          />
        </Field>
      </div>
      {allowTypeFilter && (
        <Field label="产物类型" error={errors.types} hint="缺省全部四类">
          <div className="flex flex-wrap gap-3 pt-1">
            {ALL_TYPES.map((type) => (
              <label key={type} className="flex items-center gap-1.5 text-[13px] text-foreground">
                <Checkbox
                  checked={types.includes(type)}
                  onCheckedChange={() => toggleType(type)}
                />
                {type}
              </label>
            ))}
          </div>
        </Field>
      )}
      <Field label="备注" htmlFor="m-note">
        <TextField id="m-note" value={note} onChange={setNote} />
      </Field>
    </FormDialog>
  )
}
