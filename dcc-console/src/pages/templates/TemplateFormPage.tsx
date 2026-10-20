import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { templatesApi } from '@/api/templates'
import { LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import {
  TemplateSectionEditor,
  nodesToTemplateSections,
  templateSectionsToNodes,
  type SectionNode,
} from '@/components/form/FieldTreeEditor'
import type { StandardOption } from '@/components/form/TableEditors'
import { Field, TextAreaField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { TemplateContent } from '@/types'

interface FormState {
  code: string
  name: string
  description: string
  sections: SectionNode[]
}

const EMPTY: FormState = { code: '', name: '', description: '', sections: [] }

export function TemplateFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(isEdit)
  const [submitting, setSubmitting] = useState(false)

  const { data: standards } = useAsync(
    () =>
      standardsApi.list({
        environmentId: environmentId ?? undefined,
        applicationId: applicationId ?? undefined,
      }),
    [environmentId, applicationId],
  )
  const standardOptions: StandardOption[] = (standards ?? []).map((standard) => ({
    id: standard.id,
    code: standard.code,
    name: standard.content.name,
  }))

  useEffect(() => {
    if (!isEdit || id === undefined) return
    let cancelled = false
    setLoading(true)
    templatesApi
      .get(Number(id))
      .then((view) => {
        if (cancelled) return
        setForm({
          code: view.code,
          name: view.content.name ?? '',
          description: view.content.description ?? '',
          sections: templateSectionsToNodes(view.content.sections ?? []),
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

  const handleSubmit = async () => {
    const next: Record<string, string> = {}
    if (!isEdit && !form.code.trim()) next.code = '编码不能为空'
    if (!form.name.trim()) next.name = '名称不能为空'
    setErrors(next)
    if (Object.keys(next).length > 0) return

    const content: TemplateContent = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      sections: nodesToTemplateSections(form.sections),
    }

    setSubmitting(true)
    try {
      if (isEdit && id !== undefined) {
        const view = await templatesApi.update(Number(id), { content })
        toast.success('已保存', view.code)
        navigate(`/templates/${view.id}`)
      } else {
        if (environmentId == null || applicationId == null) {
          toast.error('请先选择环境与应用')
          return
        }
        const view = await templatesApi.create(environmentId, applicationId, {
          code: form.code.trim(),
          content,
        })
        toast.success('已创建', view.code)
        navigate(`/templates/${view.id}`)
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
        title={isEdit ? '编辑接口模板' : '新建接口模板'}
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
        <Field label="编码" htmlFor="code" required={!isEdit} error={errors.code} className="max-w-sm">
          <TextField
            id="code"
            value={form.code}
            onChange={(value) => set('code', value)}
            disabled={isEdit}
            placeholder="如 TPL_STD_HEADER"
          />
        </Field>
        <Field label="名称" htmlFor="name" required error={errors.name} className="max-w-sm">
          <TextField id="name" value={form.name} onChange={(value) => set('name', value)} />
        </Field>
        <Field label="描述" htmlFor="description">
          <TextAreaField
            id="description"
            value={form.description}
            onChange={(value) => set('description', value)}
            rows={2}
          />
        </Field>
        <Field label="分层" hint="按 协议头 / 网关头 / 路由头 / 业务头 / 数据体 / 尾部 组织字段">
          <TemplateSectionEditor
            sections={form.sections}
            onChange={(value) => set('sections', value)}
            standards={standardOptions}
          />
        </Field>
      </div>
    </div>
  )
}
