import { CheckCircle2, X, XCircle } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

export interface ToastInput {
  title: string
  description?: string
  variant: 'success' | 'error'
}

interface ToastItem extends ToastInput {
  id: number
}

interface ToastContextValue {
  toast: (input: ToastInput) => void
  success: (title: string, description?: string) => void
  error: (title: string, description?: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const remove = useCallback((id: number) => {
    setItems((prev) => prev.filter((item) => item.id !== id))
  }, [])

  const push = useCallback(
    (input: ToastInput) => {
      const id = Date.now() + Math.random()
      setItems((prev) => [...prev, { ...input, id }])
      window.setTimeout(() => remove(id), 4000)
    },
    [remove],
  )

  const value = useMemo<ToastContextValue>(
    () => ({
      toast: push,
      success: (title, description) => push({ title, description, variant: 'success' }),
      error: (title, description) => push({ title, description, variant: 'error' }),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed bottom-4 right-4 z-50 flex w-80 max-w-[calc(100vw_-_2rem)] flex-col gap-2"
      >
        {items.map((item) => (
          <div
            key={item.id}
            role={item.variant === 'error' ? 'alert' : 'status'}
            className={cn(
              'flex items-start gap-2.5 rounded-lg border p-3 shadow-popover',
              'duration-200 animate-in fade-in slide-in-from-bottom-2 motion-reduce:animate-none',
              item.variant === 'error'
                ? 'border-status-danger/30 bg-status-danger-soft'
                : 'border-status-success/30 bg-status-success-soft',
            )}
          >
            {item.variant === 'error' ? (
              <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-status-danger" aria-hidden />
            ) : (
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-status-success" aria-hidden />
            )}
            <div className="min-w-0 flex-1">
              <div className="text-[13px] font-semibold text-foreground">{item.title}</div>
              {item.description && (
                <div className="mt-0.5 break-words text-[13px] text-muted-foreground">
                  {item.description}
                </div>
              )}
            </div>
            <button
              type="button"
              aria-label="关闭提示"
              onClick={() => remove(item.id)}
              className="-mr-1 -mt-0.5 shrink-0 rounded-sm p-0.5 text-muted-foreground transition-colors hover:bg-foreground/5 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20"
            >
              <X className="h-3.5 w-3.5" aria-hidden />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast 必须在 ToastProvider 内使用')
  }
  return ctx
}
