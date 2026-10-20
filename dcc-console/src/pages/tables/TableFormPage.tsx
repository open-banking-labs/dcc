import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { standardsApi } from '@/api/standards'
import { tablesApi } from '@/api/tables'
import { LoadingState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { ColumnEditor, IndexEditor, type StandardOption } from '@/components/form/TableEditors'
import { Field, TextAreaField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { TableColumn, TableContent, TableIndex } from '@/types'

interface FormState {
  code: string
  name: string
  description: string
  shardKey: string
  columns: TableColumn[]
  indexes: TableIndex[]
}

const EMPTY: FormState = {
  code: '',
  name: '',
  description: '',
  shardKey: '',
  columns: [],
  indexes: [],
}

export function TableFormPage() {
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
    tablesApi
      .get(Number(id))
      .then((view) => {
        if (cancelled) return
        setForm({
          code: view.code,
          name: view.content.name ?? '',
          description: view.content.description ?? '',
          shardKey: view.content.shardKey ?? '',
          columns: view.content.columns ?? [],
          indexes: view.content.indexes ?? [],
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
    if (!form.name.trim()) next.name = '表名不能为空'
    if (form.columns.length === 0) next.columns = '至少需要一个列'
    const names = form.columns.map((column) => column.columnName.trim())
    if (names.some((name) => !name)) next.columns = '列名不能为空'
    else if (new Set(names).size !== names.length) next.columns = '列名不能重复'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async () => {
    if (!validate()) return
    const content: TableContent = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      shardKey: form.shardKey.trim() || null,
      columns: form.columns.map((column, index) => ({ ...column, sortOrder: index })),
      indexes: form.indexes,
    }

    setSubmitting(true)
    try {
      if (isEdit && id !== undefined) {
        const view = await tablesApi.update(Number(id), { content })
        toast.success('已保存', view.code)
        navigate(`/tables/${view.id}`)
      } else {
        if (environmentId == null || applicationId == null) {
          toast.error('请先选择环境与应用')
          return
        }
        const view = await tablesApi.create(environmentId, applicationId, {
          code: form.code.trim(),
          content,
        })
        toast.success('已创建', view.code)
        navigate(`/tables/${view.id}`)
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
        title={isEdit ? '编辑表结构' : '新建表结构'}
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
        <div className="flex flex-wrap gap-3">
          <Field label="编码" htmlFor="code" required={!isEdit} error={errors.code} className="w-56">
            <TextField
              id="code"
              value={form.code}
              onChange={(value) => set('code', value)}
              disabled={isEdit}
              placeholder="如 T_CUSTOMER"
            />
          </Field>
          <Field label="表名" htmlFor="name" required error={errors.name} className="w-56">
            <TextField id="name" value={form.name} onChange={(value) => set('name', value)} />
          </Field>
          <Field label="分片键" htmlFor="shardKey" className="w-56">
            <TextField
              id="shardKey"
              value={form.shardKey}
              onChange={(value) => set('shardKey', value)}
            />
          </Field>
        </div>

        <Field label="描述" htmlFor="description">
          <TextAreaField
            id="description"
            value={form.description}
            onChange={(value) => set('description', value)}
            rows={2}
          />
        </Field>

        <Field label="列" required error={errors.columns} hint="引用数标时类型由后端按数标解析并快照">
          <ColumnEditor
            columns={form.columns}
            onChange={(columns) => set('columns', columns)}
            standards={standardOptions}
          />
        </Field>

        <Field label="索引">
          <IndexEditor indexes={form.indexes} onChange={(indexes) => set('indexes', indexes)} />
        </Field>
      </div>
    </div>
  )
}
