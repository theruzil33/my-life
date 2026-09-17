import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import RegisterPage from '../../src/pages/RegisterPage'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { AUTH_STORAGE_KEY } from '../../src/auth/sessionStorage'
import { ApiError, SERVER_ERROR_MESSAGE } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function renderRegisterPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<p>Форма входа</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function fillAndSubmit(user, { email = 'user@example.com', password = 'secret123' } = {}) {
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Пароль'), password)
  await user.click(screen.getByRole('button', { name: 'Зарегистрироваться' }))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('RegisterPage', () => {
  it('показывает подтверждение успешной регистрации и не сохраняет токен', async () => {
    authApi.register.mockResolvedValue({ id: 1, email: 'user@example.com' })
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)

    expect(authApi.register).toHaveBeenCalledWith('user@example.com', 'secret123')
    expect(await screen.findByRole('status')).toHaveTextContent('успешно зарегистрирован')
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('после успешной регистрации предлагает переход ко входу', async () => {
    authApi.register.mockResolvedValue({ id: 1, email: 'user@example.com' })
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)
    await user.click(await screen.findByRole('link', { name: 'Перейти ко входу' }))

    expect(screen.getByText('Форма входа')).toBeInTheDocument()
  })

  it('показывает сообщение о занятом email при 409 и не показывает успех', async () => {
    authApi.register.mockRejectedValue(new ApiError('Email is already registered', 409))
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Пользователь с таким email уже зарегистрирован')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('показывает сообщение gateway при 400', async () => {
    authApi.register.mockRejectedValue(
      new ApiError('Password must contain at least 8 characters', 400),
    )
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Password must contain at least 8 characters',
    )
  })

  it('не отправляет запрос при коротком пароле', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user, { password: '1234567' })

    expect(await screen.findByText('Пароль должен содержать не менее 8 символов')).toBeInTheDocument()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('блокирует повторную отправку, пока запрос выполняется', async () => {
    const { promise, resolve } = deferred()
    authApi.register.mockReturnValue(promise)
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)

    expect(screen.getByRole('button', { name: 'Выполняется регистрация…' })).toBeDisabled()

    resolve({ id: 1, email: 'user@example.com' })

    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument())
  })

  it('показывает сообщение при ошибке сервера и остаётся в неаутентифицированном состоянии', async () => {
    authApi.register.mockRejectedValue(new ApiError(SERVER_ERROR_MESSAGE, 503))
    const user = userEvent.setup()
    renderRegisterPage()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(SERVER_ERROR_MESSAGE)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })
})
