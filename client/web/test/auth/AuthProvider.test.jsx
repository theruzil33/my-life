import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { useAuth } from '../../src/auth/AuthContext'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { setSessionRefresher, setUnauthorizedHandler, ApiError, request } from '../../src/api/client'

const LEGACY_AUTH_STORAGE_KEY = 'my-life.auth'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function SessionProbe() {
  const { accessToken, email, isAuthenticated, isRestoring, login, logout } = useAuth()

  return (
    <div>
      <span data-testid="token">{accessToken ?? ''}</span>
      <span data-testid="email">{email ?? ''}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <span data-testid="restoring">{String(isRestoring)}</span>
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
  vi.resetAllMocks()
  authApi.refresh.mockRejectedValue(new ApiError('no session', 401))
  authApi.logout.mockResolvedValue(null)
})

afterEach(() => {
  setSessionRefresher(null)
  setUnauthorizedHandler(null)
  window.localStorage.clear()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  vi.unstubAllGlobals()
})

describe('AuthProvider', () => {
  it('хранит access-токен и email в памяти после успешного входа', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'войти' }))

    expect(authApi.login).toHaveBeenCalledWith('user@example.com', 'secret123')
    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('jwt-token'))
    expect(screen.getByTestId('email')).toHaveTextContent('user@example.com')
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
  })

  it('не записывает токены в localStorage', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'войти' }))
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('true'))

    expect(JSON.stringify(window.localStorage)).not.toContain('jwt-token')
    expect(window.localStorage.getItem(LEGACY_AUTH_STORAGE_KEY)).toBeNull()
  })

  it('не устанавливает сессию при ошибке входа', async () => {
    authApi.login.mockRejectedValue(new ApiError('Неверный email или пароль', 401))
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByRole('button', { name: 'войти с ошибкой' }))

    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'))
  })

  it('удаляет legacy-сессию из localStorage при запуске', async () => {
    window.localStorage.setItem(LEGACY_AUTH_STORAGE_KEY, JSON.stringify({ accessToken: 'legacy' }))

    renderProbe()

    await waitFor(() => expect(screen.getByTestId('restoring')).toHaveTextContent('false'))
    expect(window.localStorage.getItem(LEGACY_AUTH_STORAGE_KEY)).toBeNull()
  })

  it('восстанавливает сессию через refresh при запуске', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'restored-token', email: 'restored@example.com' })

    renderProbe()

    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('restored-token'))
    expect(screen.getByTestId('email')).toHaveTextContent('restored@example.com')
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('restoring')).toHaveTextContent('false')
  })

  it('остаётся неаутентифицированным, если refresh при запуске не удался', async () => {
    renderProbe()

    await waitFor(() => expect(screen.getByTestId('restoring')).toHaveTextContent('false'))
    expect(screen.getByTestId('authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('email').textContent).toBe('')
  })

  it('вызывает эндпоинт выхода и очищает сессию', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('true'))

    await user.click(screen.getByRole('button', { name: 'выйти' }))

    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'))
    expect(authApi.logout).toHaveBeenCalledTimes(1)
  })

  it('завершает локальный выход даже при недоступном gateway', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'token', email: 'user@example.com' })
    authApi.logout.mockRejectedValue(new ApiError('offline', 0))
    const user = userEvent.setup()
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('true'))

    await user.click(screen.getByRole('button', { name: 'выйти' }))

    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'))
  })

  it('обновляет сессию и повторяет запрос при 401, оставляя пользователя в системе', async () => {
    authApi.refresh.mockResolvedValueOnce({ accessToken: 'initial-token', email: 'user@example.com' })
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(fakeResponse({ status: 401, body: { message: 'auth' } }))
        .mockResolvedValueOnce(fakeResponse({ status: 200, body: { items: [] } })),
    )
    authApi.refresh.mockResolvedValueOnce({ accessToken: 'refreshed-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderProtectedApp()
    await waitFor(() => expect(screen.getByRole('button', { name: 'загрузить данные' })).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'загрузить данные' }))

    await waitFor(() => expect(screen.queryByText('Страница входа')).not.toBeInTheDocument())
  })

  it('перенаправляет на вход, если обновить сессию не удалось', async () => {
    authApi.refresh.mockResolvedValueOnce({ accessToken: 'initial-token', email: 'user@example.com' })
    authApi.refresh.mockRejectedValue(new ApiError('no refresh', 401))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({ status: 401, body: { message: 'auth' } })))
    const user = userEvent.setup()
    renderProtectedApp()
    await waitFor(() => expect(screen.getByRole('button', { name: 'загрузить данные' })).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'загрузить данные' }))

    expect(await screen.findByText('Страница входа')).toBeInTheDocument()
  })
})
