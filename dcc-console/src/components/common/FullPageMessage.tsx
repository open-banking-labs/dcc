/** 全屏单行提示（加载/错误占位）。 */
export function FullPageMessage({ text, error = false }: { text: string; error?: boolean }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-canvas px-6">
      <p className={error ? 'text-[13px] text-destructive' : 'text-[13px] text-muted-foreground'}>
        {text}
      </p>
    </div>
  )
}
