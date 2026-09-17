import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/authApi'
import { setSessionRefresher, setUnauthorizedHandler } from '../api/client'
import { AuthContext } from './AuthContext'

const LEGACY_AUTH_STORAGE_KEY = 'my-life.auth'

function clearLegacyStoredSession() {
  try {
    window.localStorage.removeItem(LEGACY_AUTH_STORAGE_KEY)
  } catch {
    // ignore storage access errors
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null)
  const [isRestoring, setIsRestoring] = useState(true)

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // локальный выход выполняется даже при недоступном gateway
    }
    setSession(null)
  }, [])

  const login = useCallback(async ({ email, password }) => {
    const data = await authApi.login(email, password)
    const nextSession = { accessToken: data.accessToken, email: data.email ?? email }
    setSession(nextSession)
    return nextSession
  }, [])

  const register = useCallback(({ email, password }) => authApi.register(email, password), [])

  useEffect(() => {
    setSessionRefresher(async () => {
      const data = await authApi.refresh()
      setSession({ accessToken: data.accessToken, email: data.email ?? null })
      return data.accessToken
    })
    setUnauthorizedHandler(() => setSession(null))
    return () => {
      setSessionRefresher(null)
      setUnauthorizedHandler(null)
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    clearLegacyStoredSession()
    authApi
      .refresh()
      .then((data) => {
        if (!cancelled) {
          setSession({ accessToken: data.accessToken, email: data.email ?? null })
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSession(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsRestoring(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const value = useMemo(
    () => ({
      accessToken: session?.accessToken ?? null,
      email: session?.email ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      isRestoring,
      login,
      register,
      logout,
    }),
    [session, isRestoring, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
