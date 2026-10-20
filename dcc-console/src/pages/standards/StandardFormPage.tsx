import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import {
  Field,
  FieldRow,
  NumberField,
  TextAreaField,
  TextField,
} from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { StandardContent } from '@/types'

interface FormState {
  code: string
  category: string
  name: string
  description: string
  dataType: string
  length: number | null
  scale: number | null
  required: boolean
  defaultValue: string
  enumValues: string
  regex: string
  exampleValue: string
}

const EMPTY: FormState = {
  code: '',
  category: '',
  name: '',
  description: '',
  dataType: '',
  length: null,
  scale: null,
  required: false,
  defaultValue: '',
  enumValues: '',
  regex: '',
  exampleValue: '',
}

export function StandardFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(isEdit)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isEdit || id === undefined) return
    let cancelled = false
    setLoading(true)
    standardsApi
      .get(Number(id))
      .then((view) => {
        if (cancelled) return
        setForm({
          code: view.code,
          category: view.category ?? '',
          name: view.content.name ?? '',
          description: view.content.description ?? '',
          dataType: view.content.dataType ?? '',
          length: view.content.length,
          scale: view.content.scale,
          required: view.content.required,
          defaultValue: view.content.defaultValue ?? '',
          enumValues: view.content.enumValues ?? '',
          regex: view.content.regex ?? '',
          exampleValue: view.content.exampleValue ?? '',
        })
      })
      .catch((err: unknown) => toast.error('加载失败', err instanceof Error ? err.message : undefined))
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, isEdit, toast])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const validate = (): boolean => {
    const next: Record<string, string> = {}
    if (!isEdit && !form.code.trim()) next.code = '编码不能为空'
    if (!form.name.trim()) next.name = '名称不能为空'
    if (!form.dataType.trim()) next.dataType = '数据类型不能为空'
    if (form.length !== null && form.length < 0) next.length = '长度不能为负'
    if (form.scale !== null && form.scale < 0) next.scale = '精度不能为负'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const buildContent = (): StandardContent => ({
    name: form.name.trim(),
    description: form.description.trim() || null,
    dataType: form.dataType.trim(),
    length: form.length,
    scale: form.scale,
    required: form.required,
    defaultValue: form.defaultValue.trim() || null,
    enumValues: form.enumValues.trim() || null,
    regex: form.regex.trim() || null,
    exampleValue: form.exampleValue.trim() || null,
  })

  const handleSubmit = async () => {
    if (!validate()) return
    setSubmitting(true)
    try {
      if (isEdit && id !== undefined) {
        const view = await standardsApi.update(Number(id), {
          category: form.category.trim() || null,
          content: buildContent(),
        })
        toast.success('已保存', view.code)
        navigate(`/standards/${view.id}`)
      } else {
        if (environmentId == null || applicationId == null) {
          toast.error('请先选择环境与应用')
          return
        }
        const view = await standardsApi.create(environmentId, applicationId, {
          code: form.code.trim(),
          category: form.category.trim() || null,
          content: buildContent(),
        })
        toast.success('已创建', view.code)
        navigate(`/standards/${view.id}`)
      }
    } catch (err) {
      toast.error('保存失败', err instanceof Error ? err.message : undefined)
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="p-6">
        <LoadingState />
      </div>
    )
  }

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title={isEdit ? '编辑数标' : '新建数标'}
        actions={
          <>
            <Button size="sm" variant="outline" onClick={() => navigate(-1)} disabled={submitting}>
              取消
            </Button>
            <Button size="sm" variant="primary" onClick={() => void handleSubmit()} disabled={submitting}>
              {submitting ? '保存中…' : '保存'}
            </Button>
          </>
        }
      />

      <div className="space-y-4 rounded-md border border-border bg-card p-4">
        <FieldRow columns={3}>
          <Field label="编码" htmlFor="code" required={!isEdit} error={errors.code}>
            <TextField
              id="code"
              value={form.code}
              onChange={(value) => set('code', value)}
              disabled={isEdit}
              placeholder="如 CUST_NAME"
            />
          </Field>
          <Field label="分类" htmlFor="category">
            <TextField id="category" value={form.category} onChange={(value) => set('category', value)} />
          </Field>
          <Field label="数据类型" htmlFor="dataType" required error={errors.dataType}>
            <TextField
              id="dataType"
              value={form.dataType}
              onChange={(value) => set('dataType', value)}
              placeholder="如 VARCHAR"
            />
          </Field>
        </FieldRow>

        <FieldRow columns={2}>
          <Field label="名称" htmlFor="name" required error={errors.name}>
            <TextField id="name" value={form.name} onChange={(value) => set('name', value)} />
          </Field>
          <Field label="长度" htmlFor="length" error={errors.length}>
            <NumberField id="length" value={form.length} onChange={(value) => set('length', value)} />
          </Field>
        </FieldRow>

        <FieldRow columns={2}>
          <Field label="精度" htmlFor="scale" error={errors.scale}>
            <NumberField id="scale" value={form.scale} onChange={(value) => set('scale', value)} />
          </Field>
          <Field label="默认值" htmlFor="defaultValue">
            <TextField
              id="defaultValue"
              value={form.defaultValue}
              onChange={(value) => set('defaultValue', value)}
            />
          </Field>
        </FieldRow>

        <Field label="描述" htmlFor="description">
          <TextAreaField
            id="description"
            value={form.description}
            onChange={(value) => set('description', value)}
            rows={2}
          />
        </Field>

        <FieldRow columns={2}>
          <Field label="枚举值" htmlFor="enumValues" hint="多个用逗号分隔，如 1,2,3">
            <TextField
              id="enumValues"
              value={form.enumValues}
              onChange={(value) => set('enumValues', value)}
            />
          </Field>
          <Field label="正则" htmlFor="regex">
            <TextField id="regex" value={form.regex} onChange={(value) => set('regex', value)} />
          </Field>
        </FieldRow>

        <FieldRow columns={2}>
          <Field label="示例值" htmlFor="exampleValue">
            <TextField
              id="exampleValue"
              value={form.exampleValue}
              onChange={(value) => set('exampleValue', value)}
            />
          </Field>
          <Field label="约束" htmlFor="required">
            <label className="flex h-8 items-center gap-2 text-sm text-foreground">
              <Checkbox
                id="required"
                checked={form.required}
                onCheckedChange={(checked) => set('required', checked === true)}
              />
              必填
            </label>
          </Field>
        </FieldRow>
      </div>
    </div>
  )
}
