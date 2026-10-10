import { useState } from 'react'

import { generationApi } from '@/api/generation'
import { CodeBlock } from '@/components/common/CodeBlock'
import { EmptyState } from '@/components/feedback/StateViews'
import { useToast } from '@/components/feedback/Toast'
import { Field, NumberField, TextField } from '@/components/form/fields'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/button'
import { useResourceContext } from '@/hooks/useResourceContext'
import type { GeneratedSource } from '@/types'

export function GenerationPage() {
  const toast = useToast()
  const { environmentId, applicationId } = useResourceContext()

  const [standardId, setStandardId] = useState<number | null>(null)
  const [validation, setValidation] = useState<GeneratedSource | null>(null)
  const [validationLoading, setValidationLoading] = useState(false)

  const [interfaceId, setInterfaceId] = useState<number | null>(null)
  const [dtoSources, setDtoSources] = useState<GeneratedSource[] | null>(null)
  const [dtoLoading, setDtoLoading] = useState(false)

  const [openApi, setOpenApi] = useState<string | null>(null)
  const [openApiLoading, setOpenApiLoading] = useState(false)

  const [jarVersion, setJarVersion] = useState('')
  const [jarLoading, setJarLoading] = useState(false)

  const genValidation = async () => {
    if (standardId == null) {
      toast.error('请输入数标 ID')
      return
    }
    setValidationLoading(true)
    try {
      setValidation(await generationApi.validation(standardId))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setValidationLoading(false)
    }
  }

  const genDto = async () => {
    if (interfaceId == null) {
      toast.error('请输入接口 ID')
      return
    }
    setDtoLoading(true)
    try {
      setDtoSources(await generationApi.dto(interfaceId))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setDtoLoading(false)
    }
  }

  const genOpenApi = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setOpenApiLoading(true)
    try {
      setOpenApi(await generationApi.openApi(environmentId, applicationId))
    } catch (err) {
      toast.error('生成失败', err instanceof Error ? err.message : undefined)
    } finally {
      setOpenApiLoading(false)
    }
  }

  const downloadJar = async () => {
    if (environmentId == null || applicationId == null) {
      toast.error('请先选择环境与应用')
      return
    }
    setJarLoading(true)
    try {
      const blob = await generationApi.downloadJar(
        environmentId,
        applicationId,
        jarVersion.trim() || undefined,
      )
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'dcc-bundle.jar'
      anchor.click()
      URL.revokeObjectURL(url)
      toast.success('已下载')
    } catch (err) {
      toast.error('下载失败', err instanceof Error ? err.message : undefined)
    } finally {
      setJarLoading(false)
    }
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader title="产物生成" description="校验类 / DTO / OpenAPI / JAR 打包" />

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">数标校验类</h3>
        <div className="flex items-end gap-3">
          <Field label="数标 ID" htmlFor="std-id" className="w-40">
            <NumberField id="std-id" value={standardId} onChange={setStandardId} />
          </Field>
          <Button size="sm" onClick={() => void genValidation()} disabled={validationLoading}>
            {validationLoading ? '生成中…' : '生成'}
          </Button>
        </div>
        {validation && <CodeBlock code={validation.content} language={validation.fileName} />}
      </section>

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">接口 DTO</h3>
        <div className="flex items-end gap-3">
          <Field label="接口 ID" htmlFor="iface-id" className="w-40">
            <NumberField id="iface-id" value={interfaceId} onChange={setInterfaceId} />
          </Field>
          <Button size="sm" onClick={() => void genDto()} disabled={dtoLoading}>
            {dtoLoading ? '生成中…' : '生成'}
          </Button>
        </div>
        {dtoSources &&
          (dtoSources.length === 0 ? (
            <EmptyState text="暂无数据" />
          ) : (
            dtoSources.map((source) => (
              <CodeBlock key={source.fileName} code={source.content} language={source.fileName} />
            ))
          ))}
      </section>

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">OpenAPI 文档</h3>
        <p className="text-xs text-muted-foreground">使用顶栏选择的环境与应用。</p>
        <Button size="sm" onClick={() => void genOpenApi()} disabled={openApiLoading}>
          {openApiLoading ? '生成中…' : '生成'}
        </Button>
        {openApi && <CodeBlock code={openApi} language="json" />}
      </section>

      <section className="space-y-3 rounded-md border border-border bg-card p-4">
        <h3 className="text-sm font-medium text-foreground">JAR 打包</h3>
        <div className="flex items-end gap-3">
          <Field label="版本（可选）" htmlFor="jar-version" className="w-40">
            <TextField id="jar-version" value={jarVersion} onChange={setJarVersion} placeholder="留空取当前" />
          </Field>
          <Button size="sm" variant="outline" onClick={() => void downloadJar()} disabled={jarLoading}>
            {jarLoading ? '打包中…' : '下载 JAR'}
          </Button>
        </div>
      </section>
    </div>
  )
}
