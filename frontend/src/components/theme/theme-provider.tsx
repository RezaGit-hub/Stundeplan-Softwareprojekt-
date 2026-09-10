// Eigener Theme-Provider (kein next-themes): Klasse .dark am <html>, localStorage 'sp-theme', Werte light|dark|system.
// ?theme=light|dark in der URL (auch hinter #/) gilt beim ersten Rendern, ohne gespeichert zu werden.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Theme = 'light' | 'dark' | 'system'
const KEY = 'sp-theme'
const media = () => window.matchMedia('(prefers-color-scheme: dark)')

function themeFromUrl(): Theme | null {
  const { search, hash } = window.location
  const q = search || (hash.includes('?') ? hash.slice(hash.indexOf('?')) : '')
  const v = new URLSearchParams(q).get('theme')
  return v === 'light' || v === 'dark' ? v : null
}
function themeFromStorage(): Theme {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'light' || v === 'dark' || v === 'system') return v
  } catch { /* privater Modus o. ä. */ }
  return 'system'
}
function apply(theme: Theme) {
  const dark = theme === 'dark' || (theme === 'system' && media().matches)
  document.documentElement.classList.toggle('dark', dark)
}

interface ThemeContextValue {
  theme: Theme
  /** tatsächlich sichtbares Theme */
  resolved: 'light' | 'dark'
  setTheme(t: Theme): void
}
const ThemeContext = createContext<ThemeContextValue | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => themeFromUrl() ?? themeFromStorage())
  const [systemDark, setSystemDark] = useState(() => media().matches)

  useEffect(() => {
    apply(theme)
    const m = media()
    const onChange = (e: MediaQueryListEvent) => {
      setSystemDark(e.matches)
      if (theme === 'system') apply('system')
    }
    m.addEventListener('change', onChange)
    return () => m.removeEventListener('change', onChange)
  }, [theme])

  const setTheme = useCallback((t: Theme) => {
    setThemeState(t)
    try { localStorage.setItem(KEY, t) } catch { /* ignorieren */ }
  }, [])

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, resolved: theme === 'system' ? (systemDark ? 'dark' : 'light') : theme, setTheme }),
    [theme, systemDark, setTheme],
  )
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme braucht einen ThemeProvider')
  return ctx
}
