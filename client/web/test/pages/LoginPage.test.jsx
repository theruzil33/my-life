import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LoginPage from '../../src/pages/LoginPage'
import HomePage from '../../src/pages/HomePage'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { ApiError, NETWORK_ERROR_MESSAGE } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<p>Форма регистрации</p>} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <HomePage />
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function fillAndSubmit(user, { email = 'user@example.com', password = 'secret123' } = {}) {
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Пароль'), password)
  await user.click(screen.getByRole('button', { name: 'Войти' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  authApi.refresh.mockRejectedValue(new ApiError('no session', 401))
})

describe('LoginPage', () => {
  it('переводит в аутентифицированное состояние при успешном входе', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderLoginPage()

    await fillAndSubmit(user)

    expect(authApi.login).toHaveBeenCalledWith('user@example.com', 'secret123')
    expect(await screen.findByText('Вы вошли в систему')).toBeInTheDocument()
    expect(screen.getByText(/user@example\.com/)).toBeInTheDocument()
    expect(JSON.stringify(window.localStorage)).not.toContain('jwt-token')
  })

  it('показывает единое сообщение об ошибке при 401 и не устанавливает сессию', async () => {
    authApi.login.mockRejectedValue(new ApiError('Invalid email or password', 401))
    const user = userEvent.setup()
    renderLoginPage()

    await fillAndSubmit(user, { password: 'wrong-password' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный email или пароль')
    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
  })

  it('не отправляет запрос при незаполненных полях', async () => {
    const user = userEvent.setup()
    renderLoginPage()

    await user.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByText('Введите email')).toBeInTheDocument()
    expect(screen.getByText('Введите пароль')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('не отправляет запрос при некорректном email', async () => {
    const user = userEvent.setup()
    renderLoginPage()

    await fillAndSubmit(user, { email: 'user.example.com' })

    expect(await screen.findByText('Введите корректный email')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('блокирует повторную отправку, пока запрос выполняется', async () => {
    const { promise, resolve } = deferred()
    authApi.login.mockReturnValue(promise)
    const user = userEvent.setup()
    renderLoginPage()

    await fillAndSubmit(user)

    expect(screen.getByRole('button', { name: 'Выполняется вход…' })).toBeDisabled()
    expect(authApi.login).toHaveBeenCalledTimes(1)

    resolve({ accessToken: 'jwt-token', email: 'user@example.com' })

    await waitFor(() => expect(screen.getByText('Вы вошли в систему')).toBeInTheDocument())
  })

  it('показывает сообщение о невозможности выполнить запрос при сетевой ошибке', async () => {
    authApi.login.mockRejectedValue(new ApiError(NETWORK_ERROR_MESSAGE, 0))
    const user = userEvent.setup()
    renderLoginPage()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
  })
})
