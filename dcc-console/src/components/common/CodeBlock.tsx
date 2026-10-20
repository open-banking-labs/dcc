import { Check, Copy } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

/** 等宽代码块，带复制按钮。 */
export function CodeBlock({ code, language = 'text' }: { code: string; language?: string }) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      // 剪贴板不可用时忽略（如非安全上下文）。
    }
  }

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-secondary">
      <div className="flex items-center justify-between border-b border-border bg-secondary py-1 pl-3 pr-1.5">
        <span className="font-mono text-[12px] font-semibold text-muted-foreground">
          {language}
        </span>
        <Button size="sm" variant="ghost" onClick={() => void copy()}>
          {copied ? <Check className="h-3.5 w-3.5" aria-hidden /> : <Copy className="h-3.5 w-3.5" aria-hidden />}
          {copied ? '已复制' : '复制'}
        </Button>
      </div>
      <pre className="max-h-96 overflow-auto bg-secondary p-3 font-mono text-[12px] leading-relaxed">
        <code>{code}</code>
      </pre>
    </div>
  )
}
