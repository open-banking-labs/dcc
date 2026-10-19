import type { ColumnDef } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'

import { casesApi } from '@/api/cases'
import { CodeBlock } from '@/components/common/CodeBlock'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { PermissionGate } from '@/components/common/PermissionGate'
import { StatusBadge } from '@/components/common/StatusBadge'
import { DataTable } from '@/components/data/DataTable'
import { useToast } from '@/components/feedback/Toast'
import { FormDialog } from '@/components/form/FormDialog'
import { Field, SelectField, TextAreaField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAsync } from '@/hooks/useAsync'
import { useResourceContext } from '@/hooks/useResourceContext'
import { formatDateTime } from '@/lib/format'
import { CASE_FEATURE_LABEL } from '@/lib/labels'
import { PERMISSION } from '@/lib/permissions'
import type {
  CaseAdoptionView,
  CaseReachView,
  CaseView,
  CreateCaseBody,
  ReferenceCaseFeature,
} from '@/types/case'

const FEATURES: ReferenceCaseFeature[] = [
  'DATA_STANDARD',
  'TABLE_STRUCTURE',
  'INTERFACE',
  'INTERFACE_TEMPLATE',
  'SKELETON',
]

interface CaseForm {
  feature: string
  code: string
  name: string
  description: string
  caseVersion: string
  content: string
  enabled: boolean
}

const EMPTY_CASE: CaseForm = {
  feature: 'DATA_STANDARD',
  code: '',
  name: '',
  description: '',
  caseVersion: '1.0.0',
  content: '',
  enabled: true,
}

/** 案例库：平台侧维护范例，租户侧可采纳为草稿（副本不会随案例升级自动改写）。 */
export function CasesPage() {
  const toast = useToast()

  return (
    <div className="space-y-4 p-6">
      <PageHeader
        title="指导性案例"
        description="平台提供的参考实现：租户管理员可以采纳为草稿，也可以忽略后从零维护"
      />
      <Tabs defaultValue="library">
        <TabsList>
          <TabsTrigger value="library">案例库</TabsTrigger>
          <TabsTrigger value="adoptions">我的采纳</TabsTrigger>
        </TabsList>
        <TabsContent value="library">
          <LibraryPane toast={toast} />
        </TabsContent>
        <TabsContent value="adoptions">
          <AdoptionsPane />
        </TabsContent>
      </Tabs>
    </div>
  )
}

type Toast = ReturnType<typeof useToast>

function LibraryPane({ toast }: { toast: Toast }) {
  const { environments, applications } = useResourceContext()
  const { data, loading, error, reload } = useAsync(() => casesApi.list(), [])
  const rows = useMemo(() => data ?? [], [data])

  const [viewing, setViewing] = useState<CaseView | null>(null)
  const [editing, setEditing] = useState<CaseView | 'new' | null>(null)
  const [form, setForm] = useState<CaseForm>(EMPTY_CASE)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [adopting, setAdopting] = useState<CaseView | null>(null)
  const [adoptForm, setAdoptForm] = useState({ environmentId: '', applicationId: '', templateId: '', code: '' })
  const [reachOf, setReachOf] = useState<CaseView | null>(null)
  const [removing, setRemoving] = useState<CaseView | null>(null)
  const [busy, setBusy] = useState(false)

  const envOptions = environments.map((env) => ({ value: String(env.id), label: env.name }))
  const appOptions = applications.map((app) => ({ value: String(app.id), label: app.name }))

  const openCreate = () => {
    setForm(EMPTY_CASE)
    setErrors({})
    setEditing('new')
  }

  const openEdit = (row: CaseView) => {
    setForm({
      feature: row.feature,
      code: row.code,
      name: row.name,
      description: row.description ?? '',
      caseVersion: row.caseVersion,
      content: row.content,
      enabled: row.enabled,
    })
    setErrors({})
    setEditing(row)
  }

  const save = async () => {
    const next: Record<string, string> = {}
    if (!form.code.trim()) next.code = '案例编码不能为空'
    if (!form.name.trim()) next.name = '名称不能为空'
    if (!form.content.trim()) next.content = '内容不能为空'
    setErrors(next)
    if (Object.keys(next).length > 0) return

    setBusy(true)
    try {
      if (editing === 'new') {
        const body: CreateCaseBody = {
          feature: form.feature as ReferenceCaseFeature,
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim() || null,
          content: form.content,
          caseVersion: form.caseVersion.trim() || null,
          enabled: form.enabled,
        }
        await casesApi.create(body)
        toast.success('案例已创建', form.code.trim())
      } else if (editing) {
        await casesApi.update(editing.id, {
          name: form.name.trim(),
          description: form.description.trim() || null,
          content: form.content,
          caseVersion: form.caseVersion.trim() || null,
          enabled: form.enabled,
        })
        toast.success('案例已更新', form.code.trim())
      }
      setEditing(null)
      reload()
    } catch (err) {
      toast.error('保存失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const openAdopt = (row: CaseView) => {
    setAdoptForm({
      environmentId: environments[0] ? String(environments[0].id) : '',
      applicationId: applications[0] ? String(applications[0].id) : '',
      templateId: '',
      code: '',
    })
    setAdopting(row)
  }

  const adopt = async () => {
    if (!adopting) return
    if (optingNeedsTarget(adopting.feature) && (!adoptForm.environmentId || !adoptForm.applicationId)) {
      toast.error('请选择目标环境与应用')
      return
    }
    setBusy(true)
    try {
      const result = await casesApi.adopt(adopting.id, {
        environmentId: adoptForm.environmentId ? Number(adoptForm.environmentId) : null,
        applicationId: adoptForm.applicationId ? Number(adoptForm.applicationId) : null,
        templateId: adoptForm.templateId ? Number(adoptForm.templateId) : null,
        code: adoptForm.code.trim() || null,
      })
      toast.success('已采纳为草稿', result.created.join('、'))
      setAdopting(null)
      reload()
    } catch (err) {
      toast.error('采纳失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!removing) return
    setBusy(true)
    try {
      await casesApi.remove(removing.id)
      toast.success('案例已删除', removing.code)
      setRemoving(null)
      reload()
    } catch (err) {
      toast.error('删除失败', err instanceof Error ? err.message : undefined)
    } finally {
      setBusy(false)
    }
  }

  const columns: ColumnDef<CaseView, any>[] = [
    {
      id: 'code',
      header: '案例',
      accessorFn: (row) => row.code,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-mono text-xs">{row.original.code}</span>
          <span className="text-xs text-muted-foreground">{row.original.name}</span>
        </div>
      ),
    },
    {
      id: 'feature',
      header: '功能',
      accessorFn: (row) => row.feature,
      cell: ({ row }) => CASE_FEATURE_LABEL[row.original.feature],
    },
    {
      id: 'caseVersion',
      header: '版本',
      accessorFn: (row) => row.caseVersion,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.caseVersion}</span>,
    },
    {
      id: 'enabled',
      header: '状态',
      accessorFn: (row) => row.enabled,
      cell: ({ row }) => (
        <StatusBadge kind="standard" status={row.original.enabled ? 'PUBLISHED' : 'DEPRECATED'} />
      ),
    },
    {
      id: 'description',
      header: '说明',
      accessorFn: (row) => row.description ?? '',
      cell: ({ row }) => <span className="text-muted-foreground">{row.original.description ?? '—'}</span>,
    },
    {
      id: 'actions',
      header: '操作',
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex justify-end gap-1">
          <Button size="sm" variant="ghost" onClick={() => setViewing(row.original)}>
            详情
          </Button>
          <PermissionGate authority={PERMISSION.caseAdopt}>
            <Button size="sm" variant="ghost" onClick={() => openAdopt(row.original)}>
              采纳
            </Button>
          </PermissionGate>
          <PermissionGate authority={PERMISSION.caseWrite}>
            <Button size="sm" variant="ghost" onClick={() => setReachOf(row.original)}>
              采纳情况
            </Button>
            <Button size="sm" variant="ghost" onClick={() => openEdit(row.original)}>
              编辑
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="text-destructive hover:text-destructive"
              onClick={() => setRemoving(row.original)}
            >
              删除
            </Button>
          </PermissionGate>
        </div>
      ),
    },
  ]

  return (
    <>
      <div className="flex items-center justify-between pb-3">
        <p className="text-xs text-muted-foreground">
          案例是平台参考物：采纳只在租户内生成草稿，之后案例升级不会改写你的副本（只会在「我的采纳」里标记 stale）。
        </p>
        <PermissionGate authority={PERMISSION.caseWrite}>
          <Button size="sm" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            新建案例
          </Button>
        </PermissionGate>
      </div>

      <DataTable
        columns={columns}
        data={rows}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="案例库为空"
      />

      <Dialog open={viewing !== null} onOpenChange={(open) => !open && setViewing(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              案例 · {viewing?.code} (v{viewing?.caseVersion})
            </DialogTitle>
            <DialogDescription>{viewing?.description ?? viewing?.name}</DialogDescription>
          </DialogHeader>
          {viewing && <CodeBlock code={viewing.content} language={viewing.feature.toLowerCase()} />}
        </DialogContent>
      </Dialog>

      <FormDialog
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        title={editing === 'new' ? '新建案例' : '编辑案例'}
        description="内容是该功能的 payload（JSON）。SKELETON 用于环境/应用骨架范例。"
        submitting={busy}
        onSubmit={() => void save()}
        widthClass="max-w-2xl"
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="功能" htmlFor="case-feature" required>
            <SelectField
              id="case-feature"
              value={form.feature}
              onChange={(value) => setForm({ ...form, feature: value })}
              options={FEATURES.map((value) => ({ value, label: CASE_FEATURE_LABEL[value] }))}
              disabled={editing !== 'new'}
            />
          </Field>
          <Field label="案例编码" htmlFor="case-code" required error={errors.code}>
            <TextField
              id="case-code"
              value={form.code}
              onChange={(value) => setForm({ ...form, code: value })}
              placeholder="如 std-account-no"
              disabled={editing !== 'new'}
            />
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="名称" htmlFor="case-name" required error={errors.name}>
            <TextField id="case-name" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
          </Field>
          <Field label="案例版本" htmlFor="case-version">
            <TextField
              id="case-version"
              value={form.caseVersion}
              onChange={(value) => setForm({ ...form, caseVersion: value })}
              placeholder="1.0.0"
            />
          </Field>
        </div>
        <Field label="说明" htmlFor="case-desc">
          <TextField
            id="case-desc"
            value={form.description}
            onChange={(value) => setForm({ ...form, description: value })}
          />
        </Field>
        <Field label="内容 (JSON)" htmlFor="case-content" required error={errors.content}>
          <TextAreaField
            id="case-content"
            value={form.content}
            onChange={(value) => setForm({ ...form, content: value })}
            rows={8}
          />
        </Field>
      </FormDialog>

      <FormDialog
        open={adopting !== null}
        onOpenChange={(open) => !open && setAdopting(null)}
        title={`采纳案例 · ${adopting?.code ?? ''}`}
        description="在目标环境/应用中生成草稿；版本记录会写明来源于哪个案例。"
        submitting={busy}
        submitText="采纳"
        onSubmit={() => void adopt()}
      >
        {adopting && optingNeedsTarget(adopting.feature) && (
          <div className="grid grid-cols-2 gap-3">
            <Field label="目标环境" htmlFor="adopt-env" required>
              <SelectField
                id="adopt-env"
                value={adoptForm.environmentId}
                onChange={(value) => setAdoptForm({ ...adoptForm, environmentId: value })}
                options={envOptions}
                placeholder="选择"
              />
            </Field>
            <Field label="目标应用" htmlFor="adopt-app" required>
              <SelectField
                id="adopt-app"
                value={adoptForm.applicationId}
                onChange={(value) => setAdoptForm({ ...adoptForm, applicationId: value })}
                options={appOptions}
                placeholder="选择"
              />
            </Field>
          </div>
        )}
        {adopting?.feature === 'INTERFACE' && (
          <Field label="接口模板 ID" htmlFor="adopt-template" hint="采纳接口时需要指定它使用的模板">
            <TextField
              id="adopt-template"
              value={adoptForm.templateId}
              onChange={(value) => setAdoptForm({ ...adoptForm, templateId: value })}
            />
          </Field>
        )}
        {adopting && optingNeedsTarget(adopting.feature) && (
          <Field label="业务编码（可选）" htmlFor="adopt-code" hint="留空则使用案例编码；重复采纳时可换一个">
            <TextField
              id="adopt-code"
              value={adoptForm.code}
              onChange={(value) => setAdoptForm({ ...adoptForm, code: value })}
            />
          </Field>
        )}
      </FormDialog>

      <ReachDialog referenceCase={reachOf} onClose={() => setReachOf(null)} />

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null)
        }}
        title="删除案例"
        description={removing ? `确认删除案例「${removing.code}」？已采纳的副本不受影响。` : undefined}
        confirmText="删除"
        loading={busy}
        onConfirm={() => void remove()}
      />
    </>
  )
}

function AdoptionsPane() {
  const { data, loading, error, reload } = useAsync(() => casesApi.myAdoptions(), [])
  const rows = data ?? []

  const columns: ColumnDef<CaseAdoptionView, any>[] = [
    {
      id: 'caseCode',
      header: '案例',
      accessorFn: (row) => row.caseCode,
      cell: ({ row }) => <span className="font-mono text-xs">{row.original.caseCode}</span>,
    },
    {
      id: 'artifact',
      header: '我的对象',
      accessorFn: (row) => `${row.artifactType}:${row.artifactCode ?? ''}`,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="text-xs text-muted-foreground">{CASE_FEATURE_LABEL[row.original.artifactType]}</span>
          <span className="font-mono text-xs">{row.original.artifactCode ?? '—'}</span>
        </div>
      ),
    },
    {
      id: 'version',
      header: '采纳版本 / 当前案例版本',
      accessorFn: (row) => row.adoptedCaseVersion,
      cell: ({ row }) => (
        <span className="font-mono text-xs">
          {row.original.adoptedCaseVersion} → {row.original.currentCaseVersion ?? '—'}
        </span>
      ),
    },
    {
      id: 'stale',
      header: '同步状态',
      accessorFn: (row) => row.stale,
      cell: ({ row }) =>
        row.original.stale ? (
          <span className="text-xs font-medium text-destructive">案例已升级，副本未跟随</span>
        ) : (
          <span className="text-xs text-muted-foreground">与案例一致</span>
        ),
    },
    { id: 'adoptedBy', header: '采纳人', accessorFn: (row) => row.adoptedBy ?? '—' },
    {
      id: 'adoptedAt',
      header: '采纳时间',
      accessorFn: (row) => row.adoptedAt,
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-muted-foreground">{formatDateTime(row.original.adoptedAt)}</span>
      ),
    },
  ]

  return (
    <DataTable
      columns={columns}
      data={rows}
      loading={loading}
      error={error}
      onRetry={reload}
      emptyText="还没有采纳任何案例"
    />
  )
}

function ReachDialog({ referenceCase, onClose }: { referenceCase: CaseView | null; onClose: () => void }) {
  const { data, loading, error } = useAsync(
    () => (referenceCase ? casesApi.reach(referenceCase.id) : Promise.resolve([])),
    [referenceCase?.id],
  )
  const rows: CaseReachView[] = data ?? []

  return (
    <Dialog open={referenceCase !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>采纳情况 · {referenceCase?.code}</DialogTitle>
          <DialogDescription>
            哪些租户采纳了哪个版本。这里只显示影响面，不含租户的产物内容。
          </DialogDescription>
        </DialogHeader>
        {loading && <p className="py-3 text-sm text-muted-foreground">加载中…</p>}
        {error && <p className="py-3 text-sm text-destructive">{error}</p>}
        {!loading && !error && rows.length === 0 && (
          <p className="py-3 text-sm text-muted-foreground">还没有租户采纳这个案例</p>
        )}
        {rows.length > 0 && (
          <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
            {rows.map((row, index) => (
              <div key={`${row.tenantId}-${index}`} className="rounded-md border border-border p-2 text-sm">
                <div className="flex flex-wrap items-center gap-x-3">
                  <span className="font-mono text-xs">{row.tenantId}</span>
                  <span className="font-mono text-xs text-muted-foreground">
                    v{row.adoptedCaseVersion} → v{row.currentCaseVersion}
                  </span>
                  {row.stale && <span className="text-xs font-medium text-destructive">已过期</span>}
                  <span className="text-xs text-muted-foreground">{row.adoptedBy ?? '—'}</span>
                  <span className="text-xs text-muted-foreground">{formatDateTime(row.adoptedAt)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
        <div className="mt-2 flex justify-end">
          <Button size="sm" variant="outline" onClick={onClose}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** 内容类案例需要目标环境/应用；SKELETON 自己创建环境与应用。 */
function optingNeedsTarget(feature: ReferenceCaseFeature): boolean {
  return feature !== 'SKELETON'
}
