import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

export type ThemeMode = 'system' | 'light' | 'dark'
type ResolvedTheme = 'light' | 'dark'

/** 与 index.html 的首屏脚本共用同一个键。 */
const STORAGE_KEY = 'dcc.theme'

interface ThemeContextValue {
  mode: ThemeMode
  resolvedMode: ResolvedTheme
  setMode: (mode: ThemeMode) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function isThemeMode(value: string | null): value is ThemeMode {
  return value === 'system' || value === 'light' || value === 'dark'
}

function readStoredMode(): ThemeMode {
  if (typeof window === 'undefined') return 'system'
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return isThemeMode(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}

function systemPrefersDark(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyTheme(mode: ThemeMode, systemDark: boolean) {
  if (typeof document === 'undefined') return
  const dark = mode === 'dark' || (mode === 'system' && systemDark)
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'

  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (meta) meta.content = dark ? '#010409' : '#ffffff'
}

/**
 * 主题状态：默认跟随系统，手动选择写入 localStorage。
 * index.html 的首屏脚本会先应用一次，避免深色用户看到白屏闪烁。
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode)
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  const resolvedMode: ResolvedTheme =
    mode === 'dark' || (mode === 'system' && systemDark) ? 'dark' : 'light'

  useEffect(() => {
    if (mode !== 'system' || typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystemDark(media.matches)
    onChange()
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [mode])

  useEffect(() => {
    applyTheme(mode, systemDark)
  }, [mode, systemDark])

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next)
    // 立即应用，避免菜单收起后的下一帧才变色。
    applyTheme(next, systemDark)
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // 隐私模式或存储被禁用时，只保留当前会话内的选择。
    }
  }, [systemDark])

  const value = useMemo(
    () => ({ mode, resolvedMode, setMode }),
    [mode, resolvedMode, setMode],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme 必须在 ThemeProvider 内使用')
  }
  return context
}
