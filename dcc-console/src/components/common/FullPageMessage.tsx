/** 全屏单行提示（加载/错误占位）。 */
export function FullPageMessage({ text, error = false }: { text: string; error?: boolean }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50">
      <p className={error ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}>{text}</p>
    </div>
  )
}
