import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { interfacesApi } from '@/api/interfaces'
import { standardsApi } from '@/api/standards'
import { templatesApi } from '@/api/templates'
import { LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import {
  FieldTreeEditor,
  interfaceFieldsToNodes,
  nodesToInterfaceFields,
  type FieldNode,
} from '@/components/form/FieldTreeEditor'
import type { StandardOption } from '@/components/form/TableEditors'
import { Field, FieldRow, SelectField, TextAreaField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { InterfaceContent } from '@/types'

interface FormState {
  interfaceNo: string
  templateId: string
  name: string
  businessModule: string
  url: string
  shortName: string
  description: string
  input: FieldNode[]
  output: FieldNode[]
}

const EMPTY: FormState = {
  interfaceNo: '',
  templateId: '',
  name: '',
  businessModule: '',
  url: '',
  shortName: '',
  description: '',
  input: [],
  output: [],
}

export function InterfaceFormPage() {
  const { id } = useParams()
  const isEdit = id !== undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(isEdit)
  const [submitting, setSubmitting] = useState(false)

  const scope = { environmentId: environmentId ?? undefined, applicationId: applicationId ?? undefined }

  const { data: templates } = useAsync(() => templatesApi.list(scope), [environmentId, applicationId])
  const { data: standards } = useAsync(() => standardsApi.list(scope), [environmentId, applicationId])
  const standardOptions: StandardOption[] = (standards ?? []).map((standard) => ({
    id: standard.id,
    code: standard.code,
    name: standard.content.name,
  }))
  const templateOptions = (templates ?? []).map((template) => ({
    value: String(template.id),
    label: `${template.code} ${template.content.name}`,
  }))

  useEffect(() => {
    if (!isEdit || id === undefined) return
    let cancelled = false
    setLoading(true)
    interfacesApi
      .get(Number(id))
      .then((view) => {
        if (cancelled) return
        setForm({
          interfaceNo: view.interfaceNo,
          templateId: view.templateId != null ? String(view.templateId) : '',
          name: view.content.name ?? '',
          businessModule: view.content.businessModule ?? '',
          url: view.content.url ?? '',
          shortName: view.content.shortName ?? '',
          description: view.content.description ?? '',
          input: interfaceFieldsToNodes(view.content.input ?? []),
          output: interfaceFieldsToNodes(view.content.output ?? []),
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
    if (!isEdit && !form.interfaceNo.trim()) next.interfaceNo = '接口编号不能为空'
    if (!form.name.trim()) next.name = '名称不能为空'
    setErrors(next)
    if (Object.keys(next).length > 0) return

    const content: InterfaceContent = {
      name: form.name.trim(),
      businessModule: form.businessModule.trim() || null,
      url: form.url.trim() || null,
      shortName: form.shortName.trim() || null,
      description: form.description.trim() || null,
      input: nodesToInterfaceFields(form.input),
      output: nodesToInterfaceFields(form.output),
    }
    const templateId = form.templateId ? Number(form.templateId) : null

    setSubmitting(true)
    try {
      if (isEdit && id !== undefined) {
        const view = await interfacesApi.update(Number(id), { templateId, content })
        toast.success('已保存', view.interfaceNo)
        navigate(`/interfaces/${view.id}`)
      } else {
        if (environmentId == null || applicationId == null) {
          toast.error('请先选择环境与应用')
          return
        }
        const view = await interfacesApi.create(environmentId, applicationId, {
          interfaceNo: form.interfaceNo.trim(),
          templateId,
          content,
        })
        toast.success('已创建', view.interfaceNo)
        navigate(`/interfaces/${view.id}`)
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
        title={isEdit ? '编辑接口' : '新建接口'}
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
        <FieldRow columns={2}>
          <Field label="接口编号" htmlFor="interfaceNo" required={!isEdit} error={errors.interfaceNo}>
            <TextField
              id="interfaceNo"
              value={form.interfaceNo}
              onChange={(value) => set('interfaceNo', value)}
              disabled={isEdit}
              placeholder="如 IF_ACCT_QUERY"
            />
          </Field>
          <Field label="模板" htmlFor="templateId">
            <SelectField
              id="templateId"
              value={form.templateId}
              onChange={(value) => set('templateId', value)}
              options={templateOptions}
              placeholder="不绑定"
            />
          </Field>
        </FieldRow>

        <FieldRow columns={2}>
          <Field label="名称" htmlFor="name" required error={errors.name}>
            <TextField id="name" value={form.name} onChange={(value) => set('name', value)} />
          </Field>
          <Field label="业务模块" htmlFor="businessModule">
            <TextField
              id="businessModule"
              value={form.businessModule}
              onChange={(value) => set('businessModule', value)}
            />
          </Field>
        </FieldRow>

        <FieldRow columns={2}>
          <Field label="URL" htmlFor="url">
            <TextField id="url" value={form.url} onChange={(value) => set('url', value)} />
          </Field>
          <Field label="短名" htmlFor="shortName">
            <TextField id="shortName" value={form.shortName} onChange={(value) => set('shortName', value)} />
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

        <Field label="输入字段" hint="字段可引用数标（类型按数标解析）或内联，可嵌套子字段">
          <FieldTreeEditor
            fields={form.input}
            onChange={(value) => set('input', value)}
            standards={standardOptions}
            variant="interface"
          />
        </Field>

        <Field label="输出字段">
          <FieldTreeEditor
            fields={form.output}
            onChange={(value) => set('output', value)}
            standards={standardOptions}
            variant="interface"
          />
        </Field>
      </div>
    </div>
  )
}
