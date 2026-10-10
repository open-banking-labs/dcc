/** ISO 时间 → 本地可读时间；空值显示占位符。 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

/** 空值统一显示为 "—"。 */
export function dash(value?: string | number | null): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

/** 布尔 → 是/否。 */
export function yesNo(value: boolean | null | undefined): string {
  return value ? '是' : '否'
}
