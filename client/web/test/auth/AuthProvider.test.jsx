import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { useAuth } from '../../src/auth/AuthContext'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { AUTH_STORAGE_KEY, writeStoredSession } from '../../src/auth/sessionStorage'
import { ApiError, request } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function SessionProbe() {
  const { accessToken, email, isAuthenticated, login, logout } = useAuth()

  return (
    <div>
      <span data-testid="token">{accessToken ?? ''}</span>
      <span data-testid="email">{email ?? ''}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <button type="button" onClick={() => login({ email: 'user@example.com', password: 'secret123' })}>
        войти
      </button>
      <button
        type="button"
        onClick={() => login({ email: 'broken@example.com', password: 'secret123' }).catch(() => {})}
      >
        войти с ошибкой
      </button>
      <button type="button" onClick={logout}>
        выйти
      </button>
    </div>
  )
}

function renderProbe() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <SessionProbe />
      </AuthProvider>
    </MemoryRouter>,
  )
}

function ProtectedRequester() {
  const { accessToken } = useAuth()

  function loadProtectedData() {
    request('/tracker/habits', { requiresAuth: true, token: accessToken }).catch(() => {})
  }

  return (
    <button type="button" onClick={loadProtectedData}>
      загрузить данные
    </button>
  )
}

function renderProtectedApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Страница входа</p>} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <ProtectedRequester />
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

function fakeResponse({ status = 200, body = null } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AuthProvider', () => {
  it('сохраняет access-токен и email после успешного входа', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'jwt-token', tokenType: 'Bearer', expiresIn: 3600 })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'войти' }))

    expect(authApi.login).toHaveBeenCalledWith('user@example.com', 'secret123')
    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('jwt-token'))
    expect(screen.getByTestId('email')).toHaveTextContent('user@example.com')
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
    expect(JSON.parse(window.localStorage.getItem(AUTH_STORAGE_KEY))).toEqual({
      accessToken: 'jwt-token',
      email: 'user@example.com',
    })
  })

  it('не устанавливает сессию при ошибке входа', async () => {
    authApi.login.mockRejectedValue(new ApiError('Неверный email или пароль', 401))
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'войти с ошибкой' }))

    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'))
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('восстанавливает сессию из localStorage при запуске', () => {
    writeStoredSession({ accessToken: 'stored-token', email: 'stored@example.com' })

    renderProbe()

    expect(screen.getByTestId('token')).toHaveTextContent('stored-token')
    expect(screen.getByTestId('email')).toHaveTextContent('stored@example.com')
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
  })

  it('очищает сохранённую сессию при выходе', async () => {
    writeStoredSession({ accessToken: 'stored-token', email: 'stored@example.com' })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'выйти' }))

    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'))
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('очищает сессию и перенаправляет на вход при 401 на защищённом запросе', async () => {
    writeStoredSession({ accessToken: 'expired-token', email: 'stored@example.com' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({ status: 401, body: { message: 'nope' } })))
    const user = userEvent.setup()
    renderProtectedApp()

    expect(screen.getByRole('button', { name: 'загрузить данные' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'загрузить данные' }))

    expect(await screen.findByText('Страница входа')).toBeInTheDocument()
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    vi.unstubAllGlobals()
  })
})
