import { Plus, Trash2 } from 'lucide-react'

import { Field, NumberField, TextField } from '@/components/form/fields'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { TEMPLATE_SECTION_LABEL } from '@/lib/labels'
import type {
  InterfaceField,
  TemplateField,
  TemplateSection,
  TemplateSectionCode,
} from '@/types'

import type { StandardOption } from './TableEditors'

const TEMPLATE_SECTION_CODES: TemplateSectionCode[] = [
  'HEAD_PROTOCOL',
  'HEAD_GATEWAY',
  'HEAD_ROUTE',
  'HEAD_BUSINESS',
  'BODY',
  'TRAILER',
]

/** 编辑器内部统一节点（接口/模板字段的并集，差异用 variant 控制）。 */
export interface FieldNode {
  name: string
  standardId: number | null
  standardCode: string | null
  dataType: string | null
  length: number | null
  scale: number | null
  required: boolean
  list: boolean
  role: string
  children: FieldNode[]
}

const emptyNode = (): FieldNode => ({
  name: '',
  standardId: null,
  standardCode: null,
  dataType: null,
  length: null,
  scale: null,
  required: false,
  list: false,
  role: '',
  children: [],
})

// ----- 接口字段 <-> 节点 -----

export function interfaceFieldsToNodes(fields: InterfaceField[]): FieldNode[] {
  return fields.map((field) => ({
    name: field.name,
    standardId: field.standardId,
    standardCode: field.standardCode,
    dataType: field.dataType,
    length: field.length,
    scale: field.scale,
    required: field.required,
    list: field.list,
    role: '',
    children: interfaceFieldsToNodes(field.children ?? []),
  }))
}

export function nodesToInterfaceFields(nodes: FieldNode[]): InterfaceField[] {
  return nodes.map((node) => ({
    name: node.name,
    standardId: node.standardId,
    standardCode: node.standardCode,
    dataType: node.dataType,
    length: node.length,
    scale: node.scale,
    required: node.required,
    list: node.list,
    children: nodesToInterfaceFields(node.children),
  }))
}

// ----- 模板字段 <-> 节点 -----

function templateFieldsToNodes(fields: TemplateField[]): FieldNode[] {
  return fields.map((field) => ({
    name: field.name,
    standardId: field.standardId,
    standardCode: field.standardCode,
    dataType: field.dataType,
    length: field.length,
    scale: field.scale,
    required: field.required,
    list: false,
    role: field.role ?? '',
    children: templateFieldsToNodes(field.children ?? []),
  }))
}

function nodesToTemplateFields(nodes: FieldNode[]): TemplateField[] {
  return nodes.map((node) => ({
    name: node.name,
    standardId: node.standardId,
    standardCode: node.standardCode,
    dataType: node.dataType,
    length: node.length,
    scale: node.scale,
    required: node.required,
    role: node.role.trim() || null,
    children: nodesToTemplateFields(node.children),
  }))
}

// ----- 模板分层 <-> 节点 -----

export interface SectionNode {
  code: TemplateSectionCode
  name: string
  fields: FieldNode[]
}

export function templateSectionsToNodes(sections: TemplateSection[]): SectionNode[] {
  return sections.map((section) => ({
    code: section.code,
    name: section.name,
    fields: templateFieldsToNodes(section.fields ?? []),
  }))
}

export function nodesToTemplateSections(sections: SectionNode[]): TemplateSection[] {
  return sections.map((section) => ({
    code: section.code,
    name: section.name,
    fields: nodesToTemplateFields(section.fields),
  }))
}

// ----- 组件 -----

interface FieldTreeEditorProps {
  fields: FieldNode[]
  onChange: (fields: FieldNode[]) => void
  standards: StandardOption[]
  variant: 'interface' | 'template'
}

/** 递归字段编辑器：名称 / 引用数标 / 类型 / 长度 / 精度 / 必填 / 数组|角色 / 子字段。 */
export function FieldTreeEditor({ fields, onChange, standards, variant }: FieldTreeEditorProps) {
  const update = (index: number, patch: Partial<FieldNode>) =>
    onChange(fields.map((field, i) => (i === index ? { ...field, ...patch } : field)))
  const remove = (index: number) => onChange(fields.filter((_, i) => i !== index))
  const add = () => onChange([...fields, emptyNode()])

  return (
    <div className="space-y-2">
      {fields.length === 0 ? (
        <p className="rounded-md border border-dashed border-border px-3 py-3 text-center text-xs text-muted-foreground">
          暂无数据
        </p>
      ) : (
        fields.map((field, index) => (
          <div key={index} className="rounded-md border border-border">
            <div className="flex flex-wrap items-end gap-2 p-2">
              <div className="w-40">
                <Field label="名称">
                  <TextField value={field.name} onChange={(value) => update(index, { name: value })} />
                </Field>
              </div>
              <div className="w-40">
                <Field label="引用数标">
                  <select
                    className="flex h-8 w-full cursor-pointer rounded-md border border-input bg-background px-2.5 text-[13px] text-foreground shadow-sm outline-none transition-[border-color,box-shadow] hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"
                    value={field.standardId ?? ''}
                    onChange={(event) => {
                      const value = event.target.value
                      if (!value) {
                        update(index, { standardId: null, standardCode: null })
                        return
                      }
                      const standard = standards.find((item) => item.id === Number(value))
                      update(index, { standardId: Number(value), standardCode: standard?.code ?? null })
                    }}
                  >
                    <option value="">内联</option>
                    {standards.map((standard) => (
                      <option key={standard.id} value={standard.id}>
                        {standard.code}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
              <div className="w-24">
                <Field label="类型">
                  <TextField
                    value={field.dataType}
                    disabled={field.standardId != null}
                    onChange={(value) => update(index, { dataType: value || null })}
                  />
                </Field>
              </div>
              <div className="w-20">
                <Field label="长度">
                  <NumberField
                    value={field.length}
                    disabled={field.standardId != null}
                    onChange={(value) => update(index, { length: value })}
                  />
                </Field>
              </div>
              <div className="w-20">
                <Field label="精度">
                  <NumberField
                    value={field.scale}
                    disabled={field.standardId != null}
                    onChange={(value) => update(index, { scale: value })}
                  />
                </Field>
              </div>
              <div className="w-14">
                <Field label="必填">
                  <div className="flex h-8 items-center">
                    <Checkbox
                      checked={field.required}
                      onCheckedChange={(checked) => update(index, { required: checked === true })}
                    />
                  </div>
                </Field>
              </div>
              {variant === 'interface' ? (
                <div className="w-14">
                  <Field label="数组">
                    <div className="flex h-8 items-center">
                      <Checkbox
                        checked={field.list}
                        onCheckedChange={(checked) => update(index, { list: checked === true })}
                      />
                    </div>
                  </Field>
                </div>
              ) : (
                <div className="w-32">
                  <Field label="角色">
                    <TextField value={field.role} onChange={(value) => update(index, { role: value })} />
                  </Field>
                </div>
              )}
              <Button
                type="button"
                size="sm"
                variant="ghost"
                aria-label="删除这一项"
                className="text-destructive hover:text-destructive"
                onClick={() => remove(index)}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden />
              </Button>
            </div>
            <div className="border-t border-border p-2 pl-4">
              <div className="mb-1 text-xs text-muted-foreground">子字段</div>
              <FieldTreeEditor
                fields={field.children}
                onChange={(children) => update(index, { children })}
                standards={standards}
                variant={variant}
              />
            </div>
          </div>
        ))
      )}
      <Button type="button" size="sm" variant="outline" onClick={add}>
        <Plus className="h-4 w-4" />
        添加字段
      </Button>
    </div>
  )
}

interface TemplateSectionEditorProps {
  sections: SectionNode[]
  onChange: (sections: SectionNode[]) => void
  standards: StandardOption[]
}

/** 模板分层编辑器：分层编码 + 名称 + 该层字段树。 */
export function TemplateSectionEditor({ sections, onChange, standards }: TemplateSectionEditorProps) {
  const update = (index: number, patch: Partial<SectionNode>) =>
    onChange(sections.map((section, i) => (i === index ? { ...section, ...patch } : section)))
  const remove = (index: number) => onChange(sections.filter((_, i) => i !== index))
  const add = () => onChange([...sections, { code: 'BODY', name: '', fields: [] }])

  return (
    <div className="space-y-2">
      {sections.length === 0 ? (
        <p className="rounded-md border border-dashed border-border px-3 py-3 text-center text-xs text-muted-foreground">
          暂无数据
        </p>
      ) : (
        sections.map((section, index) => (
          <div key={index} className="rounded-md border border-border">
            <div className="flex flex-wrap items-end gap-2 border-b border-border p-2">
              <div className="w-44">
                <Field label="分层">
                  <select
                    className="flex h-8 w-full cursor-pointer rounded-md border border-input bg-background px-2.5 text-[13px] text-foreground shadow-sm outline-none transition-[border-color,box-shadow] hover:border-muted-foreground/50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"
                    value={section.code}
                    onChange={(event) => update(index, { code: event.target.value as TemplateSectionCode })}
                  >
                    {TEMPLATE_SECTION_CODES.map((code) => (
                      <option key={code} value={code}>
                        {TEMPLATE_SECTION_LABEL[code]}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
              <div className="w-44">
                <Field label="名称">
                  <TextField value={section.name} onChange={(value) => update(index, { name: value })} />
                </Field>
              </div>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                aria-label="删除这一项"
                className="text-destructive hover:text-destructive"
                onClick={() => remove(index)}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden />
              </Button>
            </div>
            <div className="p-2">
              <FieldTreeEditor
                fields={section.fields}
                onChange={(fields) => update(index, { fields })}
                standards={standards}
                variant="template"
              />
            </div>
          </div>
        ))
      )}
      <Button type="button" size="sm" variant="outline" onClick={add}>
        <Plus className="h-4 w-4" />
        添加分层
      </Button>
    </div>
  )
}
