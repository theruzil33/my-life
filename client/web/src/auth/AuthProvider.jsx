import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/authApi'
import { setUnauthorizedHandler } from '../api/client'
import { AuthContext } from './AuthContext'
import { clearStoredSession, readStoredSession, writeStoredSession } from './sessionStorage'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readStoredSession())

  const logout = useCallback(() => {
    clearStoredSession()
    setSession(null)
  }, [])

  const login = useCallback(async ({ email, password }) => {
    const token = await authApi.login(email, password)
    const nextSession = { accessToken: token.accessToken, email }
    writeStoredSession(nextSession)
    setSession(nextSession)
    return nextSession
  }, [])

  const register = useCallback(({ email, password }) => authApi.register(email, password), [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const value = useMemo(
    () => ({
      accessToken: session?.accessToken ?? null,
      email: session?.email ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
