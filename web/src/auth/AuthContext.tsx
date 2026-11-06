import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { clearTokens, getRefreshToken, refreshSession, setSessionLostHandler, storeTokens } from '../api/client'
import type { TokenResponse, User } from '../api/types'

interface AuthState {
  user: User | null
  /** true, пока восстанавливается сессия по refresh-токену при загрузке страницы */
  loading: boolean
  login: (login: string, password: string) => Promise<User>
  /** Применить токены после смены пароля */
  applyTokens: (tokens: TokenResponse) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState<boolean>(() => getRefreshToken() !== null)

  useEffect(() => {
    setSessionLostHandler(() => setUser(null))
    if (!getRefreshToken()) return
    let cancelled = false
    ;(async () => {
      try {
        if (await refreshSession()) {
          const me = await authApi.me()
          if (!cancelled) setUser(me)
        }
      } catch {
        clearTokens()
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const applyTokens = useCallback((tokens: TokenResponse) => {
    storeTokens(tokens)
    setUser(tokens.user)
  }, [])

  const login = useCallback(
    async (loginName: string, password: string) => {
      const tokens = await authApi.login(loginName, password)
      applyTokens(tokens)
      return tokens.user
    },
    [applyTokens],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      /* токен всё равно удаляем локально */
    }
    clearTokens()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, loading, login, applyTokens, logout }), [user, loading, login, applyTokens, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth вне AuthProvider')
  return ctx
}
